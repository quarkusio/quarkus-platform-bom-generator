package io.quarkus.domino.cli;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.ArtifactSet;
import io.quarkus.domino.tree.DependencyTreeProcessor;
import io.quarkus.domino.tree.DependencyTreeVisitor;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.util.GlobUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import picocli.CommandLine;

@CommandLine.Command(name = "dependency", header = "Populate Maven repository with required artifacts", description = "%n"
        + "This command will download the required Maven artifacts into a local Maven repository.")
public class Dependency implements Callable<Integer> {

    @CommandLine.Option(names = {
            "--bom" }, description = "Maven BOM dependency constraints of which should be resolved including their dependencies.", required = false)
    protected String bom;

    @CommandLine.Option(names = {
            "--roots" }, description = "Maven artifacts whose dependencies should be resolved", required = false, split = ",")
    protected List<String> roots = List.of();

    @CommandLine.Option(names = {
            "--versions" }, description = "Limit artifact versions to those matching specified glob patterns", split = ",")
    protected List<String> versions = List.of();
    private List<Pattern> versionPatterns;

    @CommandLine.Option(names = {
            "--invalid-artifacts-report"
    }, description = "Generate a report containing artifacts that couldn't be resolved")
    protected Path invalidArtifactsReport;

    @CommandLine.Option(names = {
            "--settings",
            "-s" }, description = "A path to Maven settings that should be used when initializing the Maven resolver")
    protected String settings;

    @CommandLine.Option(names = {
            "--maven-profiles",
            "-P" }, description = "Comma-separated list of Maven profiles that should be enabled when resolving dependencies")
    public String mavenProfiles;

    @CommandLine.Option(names = { "--repo-dir" }, description = "Local repository directory")
    public String repoDir;

    @CommandLine.Option(names = {
            "--resolve" }, description = "Resolve binary artifacts in addition to collecting their metadata")
    public boolean resolve;

    @CommandLine.Option(names = { "--parallel" }, description = "Resolves dependency trees in parallel", defaultValue = "true")
    public boolean parallelProcessing;

    @CommandLine.Option(names = {
            "--trace" }, description = "Trace artifacts matching specified glob patterns as dependencies", split = ",")
    protected List<String> trace = List.of();

    protected MessageWriter log = MessageWriter.info();

    @Override
    public Integer call() throws Exception {

        var resolver = getResolver();

        final ArtifactSet tracePattern;
        if (trace != null && !trace.isEmpty()) {
            var builder = ArtifactSet.builder();
            for (var exp : trace) {
                builder.include(exp);
            }
            tracePattern = builder.build();
        } else {
            tracePattern = null;
        }

        final Set<String> invalidArtifacts = invalidArtifactsReport == null ? Set.of() : new HashSet<>();
        var treeVisitor = new DependencyTreeVisitor<List<Artifact>>() {

            @Override
            public void visitTree(DependencyTreeVisit<List<Artifact>> ctx) {
                if (tracePattern != null) {
                    visitNode(ctx, ctx.getRoot(), new ArrayList<>());
                }
            }

            private void visitNode(DependencyTreeVisit<List<Artifact>> ctx, DependencyNode node, List<Artifact> branch) {
                var a = node.getArtifact();
                branch.add(a);
                if (tracePattern.contains(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(),
                        a.getVersion())) {
                    ctx.pushEvent(branch);
                    branch.remove(branch.size() - 1);
                    return;
                }
                for (var child : node.getChildren()) {
                    visitNode(ctx, child, branch);
                }
                branch.remove(branch.size() - 1);
            }

            @Override
            public void onEvent(List<Artifact> result, MessageWriter log) {
                for (int i = 0; i < result.size(); ++i) {
                    log.info(getMessage(i, result.get(i)));
                }
            }

            @Override
            public void handleResolutionFailures(Collection<Artifact> artifacts) {
                if (invalidArtifactsReport != null) {
                    for (var a : artifacts) {
                        var sb = new StringBuilder();
                        sb.append(a.getGroupId()).append(":").append(a.getArtifactId()).append(":");
                        if (!a.getClassifier().isEmpty()) {
                            sb.append(a.getClassifier()).append(":");
                        }
                        if (!ArtifactCoords.TYPE_JAR.equals(a.getExtension())) {
                            if (a.getClassifier().isEmpty()) {
                                sb.append(":");
                            }
                            sb.append(a.getExtension()).append(":");
                        }
                        sb.append(a.getVersion());
                        invalidArtifacts.add(sb.toString());
                    }
                }
            }
        };

        var treeProcessor = DependencyTreeProcessor.configure()
                .setArtifactResolver(resolver)
                .setResolveDependencies(resolve)
                .setParallelProcessing(parallelProcessing)
                .setTreeVisitor(treeVisitor);

        if (bom != null) {
            var coords = ArtifactCoords.fromString(bom);
            coords = ArtifactCoords.pom(coords.getGroupId(), coords.getArtifactId(), coords.getVersion());

            var aetherBom = getAetherPom(coords);

            var descriptor = resolver.resolveDescriptor(aetherBom);
            if (descriptor.getManagedDependencies().isEmpty()) {
                throw new RuntimeException(
                        coords.toCompactCoords() + " either does not include dependency constraints or failed to resolve");
            }

            // make sure there are no duplicates, which may happen with test-jar and tests classifier
            final Set<String> managedRoots = new HashSet<>(descriptor.getManagedDependencies().size());
            var constraints = descriptor.getManagedDependencies();
            for (var d : descriptor.getManagedDependencies()) {
                var a = d.getArtifact();
                if (isVersionSelected(a.getVersion()) && managedRoots.add(d.getArtifact().toString())) {
                    treeProcessor.addRoot(d.getArtifact(), constraints, d.getExclusions());
                }
            }
        } else if (this.roots.isEmpty()) {
            throw new IllegalArgumentException("Neither --bom nor --roots have been provided");
        }

        if (!roots.isEmpty()) {
            for (var root : this.roots) {
                var coords = ArtifactCoords.fromString(root);
                var a = new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                        coords.getType(), coords.getVersion());
                if (isVersionSelected(a.getVersion())) {
                    treeProcessor.addRoot(a);
                }
            }
        }

        treeProcessor.process();

        if (!invalidArtifacts.isEmpty() && invalidArtifactsReport != null) {
            var list = new ArrayList<>(invalidArtifacts);
            Collections.sort(list);
            invalidArtifactsReport = invalidArtifactsReport.normalize().toAbsolutePath();
            var dir = invalidArtifactsReport.getParent();
            if (dir != null) {
                try {
                    Files.createDirectories(dir);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(invalidArtifactsReport)) {
                for (var s : list) {
                    writer.write(s);
                    writer.newLine();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return 0;
    }

    private boolean isVersionSelected(String version) {
        if (versions.isEmpty()) {
            return true;
        }
        if (versionPatterns == null) {
            var patterns = new ArrayList<Pattern>(versions.size());
            for (var vp : versions) {
                patterns.add(Pattern.compile(GlobUtil.toRegexPattern(vp)));
            }
            versionPatterns = patterns;
        }
        for (var p : versionPatterns) {
            if (p.matcher(version).matches()) {
                return true;
            }
        }
        return false;
    }

    private MavenArtifactResolver getResolver() throws BootstrapMavenException {
        var config = BootstrapMavenContext.config()
                .setWorkspaceDiscovery(false)
                .setArtifactTransferLogging(false);
        if (settings != null) {
            var f = new File(settings);
            if (!f.exists()) {
                throw new IllegalArgumentException(f + " does not exist");
            }
            config.setUserSettings(f);
        }
        if (repoDir != null) {
            config.setLocalRepository(repoDir);
        }
        if (mavenProfiles != null) {
            System.setProperty(BootstrapMavenOptions.QUARKUS_INTERNAL_MAVEN_CMD_LINE_ARGS, "-P" + mavenProfiles);
        }
        return new MavenArtifactResolver(new BootstrapMavenContext(config));
    }

    private static String getMessage(int i, Artifact n) {
        var sb = new StringBuilder();
        for (int j = 0; j < i; ++j) {
            sb.append("  ");
        }
        sb.append(n.getGroupId()).append(":").append(n.getArtifactId()).append(":");
        if (!n.getClassifier().isEmpty()) {
            sb.append(n.getClassifier()).append(":");
        }
        if (!ArtifactCoords.TYPE_JAR.equals(n.getExtension())) {
            sb.append(n.getExtension()).append(":");
        }
        return sb.append(n.getVersion()).toString();
    }

    private static Artifact getAetherPom(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), ArtifactCoords.DEFAULT_CLASSIFIER,
                ArtifactCoords.TYPE_POM, coords.getVersion());
    }
}
