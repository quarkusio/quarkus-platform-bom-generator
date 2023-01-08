package io.quarkus.bom.resolver;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactResult;

public interface ArtifactResolver {

    Path getBaseDir();

    MavenArtifactResolver underlyingResolver();

    ArtifactResult resolve(Artifact a);

    ArtifactResult resolve(Artifact a, List<RemoteRepository> repos);

    ArtifactResult resolveOrNull(Artifact a);

    ArtifactDescriptorResult describe(Artifact a);
}
