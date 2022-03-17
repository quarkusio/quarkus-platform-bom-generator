package io.quarkus.bom.platform;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.ArtifactKey;
import io.quarkus.registry.catalog.ExtensionCatalog;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;

public class PlatformCatalogResolver {

    private final MavenArtifactResolver resolver;
    private final Map<ArtifactKey, ExtensionCatalog> catalogCache = new HashMap<>();

    public PlatformCatalogResolver(MavenArtifactResolver resolver) {
        this.resolver = resolver;
    }

    ExtensionCatalog resolve(Artifact artifact) throws BootstrapMavenException, IOException {
        final ArtifactKey key = new ArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                artifact.getExtension());
        ExtensionCatalog catalog = catalogCache.get(key);
        if (catalog == null) {
            catalog = ExtensionCatalog.fromFile(resolver
                    .resolve(artifact)
                    .getArtifact().getFile().toPath());
            catalogCache.put(key, catalog);
        }
        return catalog;
    }
}
