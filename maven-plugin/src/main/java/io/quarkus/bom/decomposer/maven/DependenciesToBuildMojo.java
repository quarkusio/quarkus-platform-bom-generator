package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposer.BomDecomposerConfig;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Logs artifact coordinates (one per line) that represent supported Quarkus extensions and their dependencies
 * down to a certain depth level that need to be built from source.
 * <p/>
 * The goal exposes other options that enable logging extra information, however all the extra info will be logged
 * with `#` prefix, which the tools parsing the output could treat as a comment and ignore.
 *
 */
@Mojo(name = "dependencies-to-build", threadSafe = true)
public class DependenciesToBuildMojo extends AbstractMojo {

    private static final String NOT_MANAGED = " [not managed]";

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    /**
     * Coordinates of the BOM containing Quarkus extensions. If not provided defaults to the current project's POM
     */
    @Parameter(required = true, property = "bom", defaultValue = "${project.groupId}:${project.artifactId}::pom:${project.version}")
    String bom;

    /**
     * The depth level of a dependency tree of each supported Quarkus extension to capture.
     * Setting the level to 0 will target the supported extension artifacts themselves.
     * Setting the level to 1, will target the supported extension artifacts plus their direct dependencies.
     * If the level is not specified, the default will be -1, which means all the levels.
     */
    @Parameter(required = true, property = "level", defaultValue = "-1")
    int level = -1;

    /**
     * Whether to exclude dependencies (and their transitive dependencies) that aren't managed in the BOM. The default is true.
     */
    @Parameter(required = false, property = "includeNonManaged")
    boolean includeNonManaged;

    /**
     * Whether to log the coordinates of the artifacts captured down to the depth specified. The default is true.
     */
    @Parameter(required = false, property = "logArtifactsToBeBuilt")
    boolean logArtifactsToBeBuilt = true;

    /**
     * Whether to log the dependency trees walked down to the depth specified. The default is false.
     */
    @Parameter(required = false, property = "logTrees")
    boolean logTrees;

    /**
     * Whether to log the coordinates of the artifacts below the depth specified. The default is false.
     */
    @Parameter(required = false, property = "logRemainingArtifacts")
    boolean logRemainingArtifacts;
    /**
     * Whether to log the summary at the end. The default is true.
     */
    @Parameter(required = false, property = "logSummary")
    boolean logSummary = true;
    /**
     * Whether to log the summary at the end. The default is true.
     */
    @Parameter(required = false, property = "logNonManagedVisited")
    boolean logNonManagedVisited;

    /**
     * If specified, this parameter will cause the output to be written to the path specified, instead of writing to
     * the console.
     */
    @Parameter(property = "outputFile", required = false)
    File outputFile;
    private PrintStream fileOutput;

    /**
     * Whether to append outputs into the output file or overwrite it.
     */
    @Parameter(property = "appendOutput", required = false, defaultValue = "false")
    boolean appendOutput;

    /*
     * Whether to log code repository info for the artifacts to be built from source
     */
    @Parameter(property = "logCodeRepos", required = false, defaultValue = "true")
    boolean logCodeRepos = true;

    /**
     * Artifact coordinate keys in the {@code <groupId>:artifactId[:<classifier>:[type]]} format
     * that should be excluded from the captured set of artifacts to be built from source.
     * <p>
     * NOTE: in case an excluded artifact was met while walking a dependency, the whole branch
     * starting at the excluded artifact will be ignored.
     */
    @Parameter(property = "excludeArtifacts", required = false)
    List<String> excludeArtifacts = List.of();
    private Set<ArtifactKey> excludeKeys;

    private Set<ArtifactCoords> managedCoords;
    private final Set<ArtifactCoords> allDepsToBuild = new HashSet<>();
    private final Set<ArtifactCoords> nonManagedVisited = new HashSet<>();
    private final Set<ArtifactCoords> skippedDeps = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final ArtifactCoords bomCoords = PlatformArtifacts.ensureBomArtifact(ArtifactCoords.fromString(bom));
        debug("Quarkus platform BOM %s", bomCoords);
        final ArtifactCoords catalogCoords = new ArtifactCoords(bomCoords.getGroupId(),
                PlatformArtifacts.ensureCatalogArtifactId(bomCoords.getArtifactId()), bomCoords.getVersion(), "json",
                bomCoords.getVersion());
        debug("Quarkus extension catalog %s", catalogCoords);

        final MavenArtifactResolver resolver;
        try {
            resolver = MavenArtifactResolver.builder()
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setWorkspaceDiscovery(true)
                    .setPreferPomsFromWorkspace(true)
                    .setCurrentProject(project.getFile().toString())
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }

        final Path jsonPath;
        try {
            jsonPath = resolver.resolve(new DefaultArtifact(catalogCoords.getGroupId(), catalogCoords.getArtifactId(),
                    catalogCoords.getClassifier(), catalogCoords.getType(), catalogCoords.getVersion()))
                    .getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve the extension catalog", e);
        }

        final ExtensionCatalog catalog;
        try {
            debug("Parsing extension catalog %s", jsonPath);
            catalog = ExtensionCatalog.fromFile(jsonPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + jsonPath, e);
        }

        final Artifact bomArtifact = new DefaultArtifact(bomCoords.getGroupId(), bomCoords.getArtifactId(),
                "", ArtifactCoords.TYPE_POM, bomCoords.getVersion());
        final List<Dependency> managedDeps;
        try {
            managedDeps = resolver.resolveDescriptor(bomArtifact)
                    .getManagedDependencies();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve the descriptor of " + bomCoords, e);
        }
        if (managedDeps.isEmpty()) {
            throw new MojoExecutionException(bomCoords.toCompactCoords()
                    + " does not include any managed dependency or its descriptor could not be read");
        }

        managedCoords = new HashSet<>(managedDeps.size());
        for (Dependency d : managedDeps) {
            managedCoords.add(toCoords(d.getArtifact()));
        }

        if (!excludeArtifacts.isEmpty()) {
            debug("Excluding artifacts %s", excludeArtifacts);
            excludeKeys = new HashSet<>(excludeArtifacts.size());
            for (String s : excludeArtifacts) {
                excludeKeys.add(ArtifactKey.fromString(s));
            }
        } else {
            excludeKeys = Set.of();
        }

        final List<Extension> supported = new ArrayList<>();
        catalog.getExtensions().forEach(e -> {
            if (excludeKeys.contains(e.getArtifact().getKey())) {
                return;
            }
            Object o = e.getMetadata().get("redhat-support");
            if (o == null) {
                return;
            }
            supported.add(e);

            final DependencyNode root;
            try {
                final Artifact a = new DefaultArtifact(e.getArtifact().getGroupId(),
                        e.getArtifact().getArtifactId(), e.getArtifact().getClassifier(),
                        e.getArtifact().getType(), e.getArtifact().getVersion());
                root = resolver.getSystem().collectDependencies(resolver.getSession(), new CollectRequest()
                        .setManagedDependencies(managedDeps)
                        .setRepositories(resolver.getRepositories())
                        .setRoot(new Dependency(a, JavaScopes.RUNTIME)))
                        .getRoot();
            } catch (DependencyCollectionException e1) {
                throw new RuntimeException("Failed to collect dependencies of " + e.getArtifact().toCompactCoords(), e1);
            }

            if (logTrees) {
                if (managedCoords.contains(e.getArtifact())) {
                    logComment(e.getArtifact().toCompactCoords());
                } else {
                    logComment(e.getArtifact().toCompactCoords() + NOT_MANAGED);
                }
            }

            if (addToBeBuilt(e.getArtifact())) {
                root.getChildren().forEach(n -> processNodes(n, 1));
            }

            if (logTrees) {
                logComment("");
            }
        });

        try {
            int codeReposTotal = 0;
            if (logArtifactsToBeBuilt && !allDepsToBuild.isEmpty()) {
                logComment("Artifacts to be built from source from " + bomCoords.toCompactCoords() + ":");
                if (logCodeRepos) {
                    final DecomposedBom decomposedBom = decompose(resolver, bomArtifact, managedDeps, nonManagedVisited);
                    final Map<ReleaseId, Collection<ArtifactCoords>> releaseArtifacts = getReleaseArtifacts(decomposedBom);
                    codeReposTotal = releaseArtifacts.size();
                    for (Map.Entry<ReleaseId, Collection<ArtifactCoords>> e : releaseArtifacts.entrySet()) {
                        logComment(e.getKey().origin().toString());
                        logComment(e.getKey().version().toString());
                        for (String s : toSortedStrings(e.getValue())) {
                            log(s);
                        }
                    }
                } else {
                    for (String s : toSortedStrings(allDepsToBuild)) {
                        log(s);
                    }
                }
            }

            if (logNonManagedVisited && !nonManagedVisited.isEmpty()) {
                logComment("Non-managed dependencies visited walking dependency trees:");
                final List<String> sorted = toSortedStrings(nonManagedVisited);
                for (int i = 0; i < sorted.size(); ++i) {
                    logComment((i + 1) + ") " + sorted.get(i));
                }
            }

            if (logSummary) {
                final StringBuilder sb = new StringBuilder().append("Selecting ");
                if (this.level < 0) {
                    sb.append("all the");
                } else {
                    sb.append(this.level).append(" level(s) of");
                }
                if (this.includeNonManaged) {
                    sb.append(" managed and non-managed");
                } else {
                    sb.append(" managed (stopping at the first non-managed one)");
                }
                sb.append(" dependencies of supported extensions from ").append(bomCoords.toCompactCoords())
                        .append(" will result in:");
                logComment(sb.toString());

                sb.setLength(0);
                sb.append(allDepsToBuild.size()).append(" artifacts");
                if (codeReposTotal > 0) {
                    sb.append(" from ").append(codeReposTotal).append(" code repositories");
                }
                sb.append(" to build from source");
                logComment(sb.toString());
                if (includeNonManaged && !nonManagedVisited.isEmpty()) {
                    logComment("  * " + nonManagedVisited.size() + " of which is/are not managed by the BOM");
                }
                if (!skippedDeps.isEmpty()) {
                    logComment(skippedDeps.size() + " dependency nodes skipped");
                }
                logComment((allDepsToBuild.size() + skippedDeps.size()) + " dependencies visited in total");
            }
        } finally {
            if (fileOutput != null) {
                fileOutput.close();
            }
        }
    }

    private DecomposedBom decompose(MavenArtifactResolver resolver, Artifact bomArtifact, Collection<Dependency> managed,
            Collection<ArtifactCoords> nonManaged)
            throws MojoExecutionException {
        final BomDecomposerConfig config = BomDecomposer.config()
                .bomArtifact(bomArtifact)
                .logger(new MojoMessageWriter(getLog()))
                .mavenArtifactResolver(ArtifactResolverProvider.get(resolver));

        if (nonManaged != null && !nonManaged.isEmpty()) {
            final List<Dependency> allDeps = new ArrayList<>(managed.size() + nonManaged.size());
            allDeps.addAll(managed);
            for (ArtifactCoords c : nonManaged) {
                allDeps.add(new Dependency(
                        new DefaultArtifact(c.getGroupId(), c.getArtifactId(), c.getClassifier(), c.getType(), c.getVersion()),
                        JavaScopes.COMPILE));
            }
            config.dependencies(allDeps);
        }

        DecomposedBom decomposedBom = null;
        try {
            decomposedBom = config.decompose();
        } catch (BomDecomposerException e) {
            throw new MojoExecutionException("Failed to decompose BOM " + bomArtifact, e);
        }
        return decomposedBom;
    }

    private Map<ReleaseId, Collection<ArtifactCoords>> getReleaseArtifacts(DecomposedBom decomposedBom) {
        final Map<ArtifactKey, ProjectDependency> deps = new HashMap<>();
        decomposedBom.releases().forEach(r -> r.dependencies().forEach(d -> deps.put(d.key(), d)));
        final Map<ReleaseId, Collection<ArtifactCoords>> releaseArtifacts = new HashMap<>();
        for (ArtifactCoords c : allDepsToBuild) {
            final ProjectDependency dep = deps.get(c.getKey());
            if (dep == null) {
                throw new IllegalStateException("Failed to find dependency for " + c.getKey());
            }
            releaseArtifacts.computeIfAbsent(dep.releaseId(), k -> new ArrayList<>()).add(c);
        }
        return releaseArtifacts;
    }

    private void debug(String msg, Object... args) {
        if (getLog().isDebugEnabled()) {
            if (args.length == 0) {
                getLog().debug(msg);
            } else {
                getLog().debug(String.format(msg, args));
            }
        }
    }

    private static List<String> toSortedStrings(Collection<ArtifactCoords> coords) {
        final List<String> list = new ArrayList<>(coords.size());
        for (ArtifactCoords c : coords) {
            list.add(c.toCompactCoords());
        }
        Collections.sort(list);
        return list;
    }

    private PrintStream getOutput() {
        if (outputFile == null) {
            return System.out;
        }
        if (fileOutput == null) {
            outputFile.getParentFile().mkdirs();
            try {
                fileOutput = new PrintStream(new FileOutputStream(outputFile, appendOutput), false);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Failed to open " + outputFile + " for writing", e);
            }
        }
        return fileOutput;
    }

    private void logComment(String msg) {
        log("# " + msg);
    }

    private void log(String msg) {
        getOutput().println(msg);
    }

    private void processNodes(DependencyNode node, int level) {
        final ArtifactCoords coords = toCoords(node.getArtifact());
        if (excludeKeys.contains(coords.getKey())) {
            return;
        }
        if (this.level < 0 || level <= this.level) {
            if (logTrees) {
                final StringBuilder buf = new StringBuilder();
                for (int i = 0; i < level; ++i) {
                    buf.append("  ");
                }
                buf.append(coords.toCompactCoords());
                if (!managedCoords.contains(coords)) {
                    buf.append(' ').append(NOT_MANAGED);
                }
                logComment(buf.toString());
            }
            if (!addToBeBuilt(coords)) {
                return;
            }
        } else {
            addToRemaining(coords);
        }
        for (DependencyNode child : node.getChildren()) {
            processNodes(child, level + 1);
        }
    }

    private boolean addToBeBuilt(ArtifactCoords coords) {
        final boolean managed = managedCoords.contains(coords);
        if (!managed) {
            nonManagedVisited.add(coords);
        }
        if (managed || includeNonManaged) {
            allDepsToBuild.add(coords);
            skippedDeps.remove(coords);
        } else {
            addToRemaining(coords);
            return false;
        }
        return true;
    }

    private void addToRemaining(ArtifactCoords coords) {
        if (!allDepsToBuild.contains(coords)) {
            skippedDeps.add(coords);
        }
    }

    private static ArtifactCoords toCoords(Artifact a) {
        return new ArtifactCoords(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }
}
