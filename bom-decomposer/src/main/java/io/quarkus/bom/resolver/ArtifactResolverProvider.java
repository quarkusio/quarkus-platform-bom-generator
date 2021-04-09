package io.quarkus.bom.resolver;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import java.nio.file.Path;

public class ArtifactResolverProvider {

    public static ArtifactResolver get() {
        return get(defaultResolver());
    }

    public static ArtifactResolver get(Path baseDir) {
        return get(defaultResolver(), baseDir);
    }

    private static MavenArtifactResolver defaultResolver() {
        try {
            return MavenArtifactResolver.builder().build();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
        }
    }

    public static ArtifactResolver get(MavenArtifactResolver resolver) {
        return get(resolver, null);
    }

    public static ArtifactResolver get(MavenArtifactResolver resolver, Path baseDir) {
        return DefaultArtifactResolver.newInstance(resolver, baseDir);
    }
}
