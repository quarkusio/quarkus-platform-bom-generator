package io.quarkus.domino.cli.repo;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;

public class NonResolvingDependencyTreeBuilder extends DependencyTreeBuilder {

    public NonResolvingDependencyTreeBuilder(MavenArtifactResolver resolver) {
        super(resolver);
    }

    @Override
    public DependencyNode doBuildTree(Artifact a, List<Dependency> constraints, Collection<Exclusion> exclusions) {
        try {
            return resolver.getSystem().collectDependencies(
                    resolver.getSession(),
                    createCollectRequest(a, exclusions, constraints))
                    .getRoot();
        } catch (DependencyCollectionException e) {
            throw new RuntimeException("Failed to collect dependencies of " + a, e);
        }
    }
}
