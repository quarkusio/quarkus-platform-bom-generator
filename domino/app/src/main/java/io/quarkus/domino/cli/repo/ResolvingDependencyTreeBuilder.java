package io.quarkus.domino.cli.repo;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class ResolvingDependencyTreeBuilder extends DependencyTreeBuilder {

    public ResolvingDependencyTreeBuilder(MavenArtifactResolver resolver) {
        super(resolver);
    }

    @Override
    public DependencyNode doBuildTree(Artifact a, List<Dependency> constraints, Collection<Exclusion> exclusions) {
        try {
            return resolver.getSystem().resolveDependencies(
                    resolver.getSession(),
                    new DependencyRequest().setCollectRequest(createCollectRequest(a, exclusions, constraints)))
                    .getRoot();
        } catch (DependencyResolutionException e) {
            throw new RuntimeException("Failed to resolve dependencies of " + a, e);
        }
    }
}
