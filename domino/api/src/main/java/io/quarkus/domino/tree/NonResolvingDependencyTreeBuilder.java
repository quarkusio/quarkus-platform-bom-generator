package io.quarkus.domino.tree;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;

public class NonResolvingDependencyTreeBuilder extends DependencyTreeBuilder {

    public NonResolvingDependencyTreeBuilder(MavenArtifactResolver resolver) {
        super(resolver);
    }

    @Override
    public DependencyNode doBuildTree(DependencyTreeRoot root) {
        try {
            return resolver.getSystem().collectDependencies(
                    resolver.getSession(),
                    createCollectRequest(root))
                    .getRoot();
        } catch (DependencyCollectionException e) {
            throw new RuntimeException("Failed to collect dependencies of " + root.getArtifact(), e);
        }
    }
}
