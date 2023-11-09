package io.quarkus.domino.manifest;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import java.util.Set;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

public class Playground {

    public static void main(String[] args) throws Exception {

        var mvnCtx = new BootstrapMavenContext(BootstrapMavenContext.config().setWorkspaceDiscovery(false));
        var session = new DefaultRepositorySystemSession(mvnCtx.getRepositorySystemSession());
        if (true) {
            session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
        }

        mvnCtx = new BootstrapMavenContext(BootstrapMavenContext.config()
                .setRepositorySystem(mvnCtx.getRepositorySystem())
                .setRepositorySystemSession(session)
                .setRemoteRepositoryManager(mvnCtx.getRemoteRepositoryManager())
                .setRemoteRepositories(mvnCtx.getRemoteRepositories()));

        var resolver = new MavenArtifactResolver(mvnCtx);

        ProjectDependencyResolver.builder()
                .setArtifactResolver(resolver)
                .setDependencyConfig(ProjectDependencyConfig.builder()
                        /* @formatter:off
                        .setProjectArtifacts(List.of(
                                ArtifactCoords.jar("org.acme", "acme-lib-b", "1.0.0-SNAPSHOT"),
                                ArtifactCoords.jar("org.acme", "acme-lib-a", "1.0.0-SNAPSHOT")))
                                @formatter:on */
                        .setProjectArtifacts(List.of(
                                ArtifactCoords.jar("io.quarkus", "quarkus-core-deployment", "3.2.6.Final"),
                                ArtifactCoords.jar("io.quarkus", "quarkus-core", "3.2.6.Final")))
                        //.setIncludeOptionalDeps(true)
                        .setLegacyScmLocator(true)
                        .setExcludeScopes(Set.of("test"))
                        .build())
                //.addDependencyTreeVisitor(new LoggingDependencyTreeVisitor(MessageWriter.info(), false, null))
                .addDependencyTreeVisitor(new SbomGeneratingDependencyVisitor(
                        SbomGenerator.builder()
                                .setArtifactResolver(resolver)))
                .build()
                .resolveDependencies();
    }
}
