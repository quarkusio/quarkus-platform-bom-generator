package io.quarkus.domino.inspect;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class ResolvingDependencyTreeBuilder extends DependencyTreeBuilder {

    public ResolvingDependencyTreeBuilder(MavenArtifactResolver resolver) {
        super(resolver);
    }

    @Override
    public DependencyNode doBuildTree(DependencyTreeRequest root) {
        try {
            return resolver.getSystem().resolveDependencies(
                    resolver.getSession(),
                    new DependencyRequest().setCollectRequest(createCollectRequest(root)))
                    .getRoot();
        } catch (DependencyResolutionException e) {
            StringBuilder msg = new StringBuilder().append("Failed to resolve dependencies of ")
                    .append(root.getArtifact());
            if (e.getResult().getRequest().getCollectRequest().getRoot() == null) {
                msg.append(" (as a direct dependency)");
            }
            throw new RuntimeException(msg.toString(), e);
        }
    }
}
