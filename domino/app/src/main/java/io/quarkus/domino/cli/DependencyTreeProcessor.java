package io.quarkus.domino.cli;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.cli.repo.DependencyTreeBuilder;
import io.quarkus.domino.cli.repo.DependencyTreeRoot;
import io.quarkus.domino.cli.repo.DependencyTreeVisitScheduler;
import io.quarkus.domino.cli.repo.DependencyTreeVisitor;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

public class DependencyTreeProcessor {

    public static DependencyTreeProcessor configure() {
        return new DependencyTreeProcessor();
    }

    private String settings;
    private List<String> profiles = List.of();
    private String repoDir;
    private MavenArtifactResolver resolver;
    private boolean resolveDependencies;
    private DependencyTreeBuilder treeBuilder;
    private DependencyTreeVisitor<?> treeVisitor;
    private boolean parallelProcessing;
    private MessageWriter log;
    private List<DependencyTreeRoot> roots = new ArrayList<>();

    private DependencyTreeProcessor() {
    }

    public DependencyTreeProcessor setTreeBuilder(DependencyTreeBuilder treeBuilder) {
        this.treeBuilder = treeBuilder;
        return this;
    }

    public DependencyTreeProcessor setArtifactResolver(MavenArtifactResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public DependencyTreeProcessor setResolveDependencies(boolean resolveDependencies) {
        this.resolveDependencies = resolveDependencies;
        return this;
    }

    public DependencyTreeProcessor setTreeVisitor(DependencyTreeVisitor<?> treeVisitor) {
        this.treeVisitor = treeVisitor;
        return this;
    }

    public DependencyTreeProcessor setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
        return this;
    }

    public DependencyTreeProcessor setMessageWriter(MessageWriter log) {
        this.log = log;
        return this;
    }

    public DependencyTreeProcessor addRoot(Artifact artifact) {
        return addRoot(artifact, List.of(), List.of());
    }

    public DependencyTreeProcessor addRoot(Artifact artifact, List<Dependency> constraints) {
        return addRoot(artifact, constraints, List.of());
    }

    public DependencyTreeProcessor addRoot(Artifact artifact, List<Dependency> constraints, Collection<Exclusion> exclusions) {
        this.roots.add(new DependencyTreeRoot(artifact, constraints, exclusions));
        return this;
    }

    public void process() {

        if (resolver == null) {
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
            if (profiles != null && !profiles.isEmpty()) {
                System.setProperty(BootstrapMavenOptions.QUARKUS_INTERNAL_MAVEN_CMD_LINE_ARGS, "-P" + profiles);
            }
            try {
                resolver = new MavenArtifactResolver(new BootstrapMavenContext(config));
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
            }
        }

        if (treeBuilder == null) {
            Objects.requireNonNull(resolver);
            treeBuilder = resolveDependencies ? DependencyTreeBuilder.resolvingTreeBuilder(resolver)
                    : DependencyTreeBuilder.nonResolvingTreeBuilder(resolver);
        }

        if (log == null) {
            log = MessageWriter.info();
        }

        if (treeVisitor == null) {
            treeVisitor = new DependencyTreeVisitor<Object>() {
                @Override
                public void visitTree(DependencyTreeVisit<Object> ctx) {
                }

                @Override
                public void onEvent(Object event, MessageWriter log) {
                }

                @Override
                public void handleResolutionFailures(Collection<Artifact> artifacts) {
                }
            };
        }

        var scheduler = parallelProcessing
                ? DependencyTreeVisitScheduler.parallel(treeBuilder, treeVisitor, log, roots.size())
                : DependencyTreeVisitScheduler.sequencial(treeBuilder, treeVisitor, log, roots.size());

        for (var r : roots) {
            scheduler.scheduleProcessing(r);
        }
        scheduler.waitForCompletion();
        if (!scheduler.getResolutionFailures().isEmpty()) {
            treeVisitor.handleResolutionFailures(scheduler.getResolutionFailures());
        }
    }
}
