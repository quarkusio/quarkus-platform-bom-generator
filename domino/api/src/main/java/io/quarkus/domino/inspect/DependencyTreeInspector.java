package io.quarkus.domino.inspect;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.devtools.messagewriter.MessageWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

public class DependencyTreeInspector {

    public static DependencyTreeInspector configure() {
        return new DependencyTreeInspector();
    }

    private String settings;
    private List<String> profiles = List.of();
    private String repoDir;
    private MavenArtifactResolver resolver;
    private boolean resolveDependencies;
    private DependencyTreeBuilder treeBuilder;
    private DependencyTreeVisitor<?> visitor;
    private boolean parallelProcessing;
    private MessageWriter log;
    private List<DependencyTreeRequest> roots = new ArrayList<>();
    private String progressTrackerPrefix;

    private DependencyTreeInspector() {
    }

    public DependencyTreeInspector setArtifactResolver(MavenArtifactResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public DependencyTreeInspector setResolveDependencies(boolean resolveDependencies) {
        this.resolveDependencies = resolveDependencies;
        return this;
    }

    public DependencyTreeInspector setTreeBuilder(DependencyTreeBuilder treeBuilder) {
        this.treeBuilder = treeBuilder;
        return this;
    }

    public DependencyTreeInspector setTreeVisitor(DependencyTreeVisitor<?> treeVisitor) {
        this.visitor = treeVisitor;
        return this;
    }

    public DependencyTreeInspector setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
        return this;
    }

    public DependencyTreeInspector setMessageWriter(MessageWriter log) {
        this.log = log;
        return this;
    }

    public DependencyTreeInspector inspectAsDependency(Artifact artifact) {
        return inspectAsDependency(artifact, List.of(), List.of());
    }

    public DependencyTreeInspector inspectAsDependency(Artifact artifact, List<Dependency> constraints) {
        return inspectAsDependency(artifact, constraints, List.of());
    }

    public DependencyTreeInspector inspectAsDependency(Artifact artifact, List<Dependency> constraints,
            Collection<Exclusion> exclusions) {
        return inspect(DependencyTreeRequest.ofDependency(artifact, constraints, exclusions));
    }

    public DependencyTreeInspector inspectAsRoot(Artifact artifact, List<Dependency> constraints,
            Collection<Exclusion> exclusions) {
        return inspect(DependencyTreeRequest.ofRoot(artifact, constraints, exclusions));
    }

    public DependencyTreeInspector inspectPlugin(Artifact artifact) {
        return inspect(DependencyTreeRequest.ofPlugin(artifact));
    }

    public DependencyTreeInspector inspectPlugin(Artifact artifact, Collection<Exclusion> exclusions) {
        return inspect(DependencyTreeRequest.ofPlugin(artifact, exclusions));
    }

    public DependencyTreeInspector inspect(DependencyTreeRequest request) {
        this.roots.add(request);
        return this;
    }

    public DependencyTreeInspector setProgressTrackerPrefix(String progressTrackerPrefix) {
        this.progressTrackerPrefix = progressTrackerPrefix;
        return this;
    }

    public void complete() {

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

        if (visitor == null) {
            visitor = new DependencyTreeVisitor<>() {
                @Override
                public void visit(DependencyTreeVisit<Object> ctx) {
                }

                @Override
                public void onEvent(Object event, MessageWriter log) {
                }

                @Override
                public void handleResolutionFailures(Collection<DependencyTreeError> requests) {
                }
            };
        }

        var scheduler = parallelProcessing
                ? new ParallelTreeVisitScheduler<>(
                        new DependencyTreeVisitContext<>(visitor, log),
                        roots.size(), treeBuilder, progressTrackerPrefix)
                : new SequentialTreeVisitScheduler<>(
                        new DependencyTreeVisitContext<>(visitor, log),
                        roots.size(), treeBuilder, progressTrackerPrefix);

        for (var r : roots) {
            scheduler.process(r);
        }
        scheduler.waitForCompletion();
        if (!scheduler.getResolutionFailures().isEmpty()) {
            visitor.handleResolutionFailures(scheduler.getResolutionFailures());
        }
    }
}
