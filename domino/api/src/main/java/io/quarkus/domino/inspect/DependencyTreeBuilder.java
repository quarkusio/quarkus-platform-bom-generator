package io.quarkus.domino.inspect;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

public abstract class DependencyTreeBuilder {

    private static final Artifact root = new DefaultArtifact("io.domino", "domino-tree-builder", "pom", "1");

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

    public DependencyNode buildTree(DependencyTreeRequest root) {
        var rootNode = doBuildTree(root);
        if (root.isDependency()) {
            if (rootNode.getChildren().size() != 1) {
                throw new RuntimeException("Expected a single child node but got " + rootNode.getChildren());
            }
            return rootNode.getChildren().get(0);
        }
        return rootNode;
    }

    public abstract DependencyNode doBuildTree(DependencyTreeRequest root);

    protected CollectRequest createCollectRequest(DependencyTreeRequest root) {
        var req = new CollectRequest().setManagedDependencies(root.getConstraints());
        if (root.isPlugin()) {
            try {
                req.setRepositories(resolver.getMavenContext().getRemotePluginRepositories());
            } catch (BootstrapMavenException e) {
                throw new RuntimeException(e);
            }
        } else {
            req.setRepositories(resolver.getRepositories());
        }
        var dep = new Dependency(
                root.getArtifact(),
                JavaScopes.RUNTIME,
                false,
                root.getExclusions());
        if (root.isDependency()) {
            req.setRootArtifact(DependencyTreeBuilder.root)
                    .setDependencies(List.of(dep));
        } else {
            req.setRoot(dep);
        }
        return req;
    }
}
