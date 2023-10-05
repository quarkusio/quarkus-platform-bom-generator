package io.quarkus.domino.cli.repo;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.util.artifact.JavaScopes;

public abstract class DependencyTreeBuilder {

    private static final Artifact root = new DefaultArtifact("io.domino", "resolver-root", "pom", "1.0");

    public static DependencyTreeBuilder resolvingTreeBuilder(MavenArtifactResolver resolver) {
        return new ResolvingDependencyTreeBuilder(resolver);
    }

    public static DependencyTreeBuilder nonResolvingTreeBuilder(MavenArtifactResolver resolver) {
        return new NonResolvingDependencyTreeBuilder(resolver);
    }

    protected final MavenArtifactResolver resolver;

    DependencyTreeBuilder(MavenArtifactResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver);
    }

    public DependencyNode buildTree(Artifact a, List<Dependency> constraints, Collection<Exclusion> exclusions) {
        var root = doBuildTree(a, constraints, exclusions);
        if (root.getChildren().size() != 1) {
            throw new RuntimeException("Expected a single child node but got " + root.getChildren());
        }
        return root.getChildren().get(0);
    }

    public abstract DependencyNode doBuildTree(Artifact a, List<Dependency> constraints, Collection<Exclusion> exclusions);

    protected CollectRequest createCollectRequest(Artifact a, Collection<Exclusion> exclusions,
            List<Dependency> constraints) {
        return new CollectRequest()
                .setRootArtifact(root)
                .setDependencies(List.of(
                        new Dependency(
                                a,
                                JavaScopes.RUNTIME,
                                false,
                                exclusions)))
                .setManagedDependencies(constraints)
                .setRepositories(resolver.getRepositories());
    }
}
