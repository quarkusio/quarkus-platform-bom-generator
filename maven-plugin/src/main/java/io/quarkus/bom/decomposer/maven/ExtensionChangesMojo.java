package io.quarkus.bom.decomposer.maven;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.registry.ExtensionCatalogResolver;
import io.quarkus.registry.RegistryResolutionException;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.PlatformCatalog;
import io.quarkus.registry.catalog.PlatformRelease;
import io.quarkus.registry.catalog.PlatformStream;
import io.quarkus.registry.config.RegistriesConfig;
import io.quarkus.registry.config.RegistryConfig;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;

@Mojo(name = "extension-changes", threadSafe = true, requiresProject = false)
public class ExtensionChangesMojo extends AbstractMojo {

    @Component
    RepositorySystem repoSystem;

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    /**
     * Maven artifact coordinates of a platform member BOM extensions from which
     * should be compared to a previous version of the BOM specified by either
     * {@link #previousBom} parameter or registry configured either with
     * the {@link #registryConfig} or the {@link #registry}
     */
    @Parameter(property = "bom", required = true)
    String bom;

    /**
     * Maven artifact coordinates of the previous version of the platform member BOM
     * {@link #bom} should be compared to. If not configured, the previous version of
     * the platform member BOM will be obtained from the registry configured with
     * either the {@link #registryConfig} or {@link #registry}
     */
    @Parameter(property = "previousBom", required = false)
    String previousBom;

    /**
     * Extension registry config file used to obtain the previous version of
     * the platform member BOM configured with {@link #bom}
     */
    @Parameter(property = "registryConfig", required = false)
    File registryConfig;

    /**
     * Extension registry ID that should be used to obtain the previous version of
     * the platform member BOM configured with {@link #bom}
     */
    @Parameter(property = "registry", required = false, defaultValue = "registry.quarkus.io")
    String registry;

    /**
     * File to save the report to. If not configured the output will be logged in the terminal.
     */
    @Parameter(property = "outputFile", required = false)
    File outputFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        var catalogCoords = PlatformArtifacts.ensureCatalogArtifact(ArtifactCoords.fromString(bom));

        final MavenArtifactResolver resolver;
        try {
            resolver = MavenArtifactResolver.builder()
                    .setRepositorySystem(repoSystem)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }

        final ExtensionCatalog catalog = resolveExtensionCatalog(catalogCoords, resolver);

        final ArtifactCoords previousCatalogCoords = previousBom == null
                ? getPreviousBom(resolver, catalog)
                : PlatformArtifacts.ensureCatalogArtifact(ArtifactCoords.fromString(previousBom));

        final ExtensionCatalog previousCatalog = previousCatalogCoords != null
                ? resolveExtensionCatalog(previousCatalogCoords, resolver)
                : null;

        final Map<ArtifactKey, String> extensions = getExtensions(catalog);
        final Map<ArtifactKey, String> previousExtensions;
        if (previousCatalog == null) {
            getLog().warn("Could not determine the previous version of the extension catalog");
            previousExtensions = Map.of();
        } else {
            getLog().info(
                    "Comparing " + catalog.getBom().toCompactCoords() + " to " + previousCatalog.getBom().toCompactCoords());
            previousExtensions = getExtensions(previousCatalog);
        }

        List<Dependency> platformConstraints = null;
        List<Dependency> prevPlatformConstraints = null;
        final List<ExtensionStatus> statusList = new ArrayList<>();
        for (var prev : previousExtensions.entrySet()) {
            var newVersion = extensions.remove(prev.getKey());
            if (newVersion == null) {
                statusList.add(ExtensionStatus.removed(prev.getKey(), prev.getValue()));
            } else if (newVersion.equals(prev.getValue())) {
                if (platformConstraints == null) {
                    platformConstraints = getPlatformConstraints(catalog, resolver);
                    prevPlatformConstraints = getPlatformConstraints(previousCatalog, resolver);
                }
                var artifact = new DefaultArtifact(prev.getKey().getGroupId(),
                        prev.getKey().getArtifactId(),
                        prev.getKey().getClassifier(),
                        prev.getKey().getType(),
                        prev.getValue());
                if (!getClasspath(artifact, platformConstraints, resolver)
                        .equals(getClasspath(artifact, prevPlatformConstraints, resolver))) {
                    statusList.add(ExtensionStatus.classpathUpdate(prev.getKey(), prev.getValue()));
                }
            } else {
                statusList.add(ExtensionStatus.versionUpdate(prev.getKey(), newVersion));
            }
        }
        for (var e : extensions.entrySet()) {
            statusList.add(ExtensionStatus.newStatus(e.getKey(), e.getValue()));
        }

        if (outputFile != null) {
            outputFile.getParentFile().mkdirs();
        }
        StringWriter json = null;
        try (BufferedWriter writer = outputFile == null
                ? new BufferedWriter(json = new StringWriter())
                : Files.newBufferedWriter(outputFile.toPath())) {
            CatalogMapperHelper.serialize(statusList, writer);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to serialize extension changes report to JSON", e);
        }
        if (json != null) {
            getLog().info(json.getBuffer().toString());
        }
    }

    public static class ExtensionStatus {

        public static final String STATUS_REMOVED = "removed";
        public static final String STATUS_NEW = "new";
        public static final String STATUS_VERSION_UPDATE = "version-update";
        public static final String STATUS_CLASSPATH_UPDATE = "classpath-update";

        public static ExtensionStatus removed(ArtifactKey key, String version) {
            return new ExtensionStatus(key + ":" + version, STATUS_REMOVED);
        }

        public static ExtensionStatus newStatus(ArtifactKey key, String version) {
            return new ExtensionStatus(key + ":" + version, STATUS_NEW);
        }

        public static ExtensionStatus versionUpdate(ArtifactKey key, String version) {
            return new ExtensionStatus(key + ":" + version, STATUS_VERSION_UPDATE);
        }

        public static ExtensionStatus classpathUpdate(ArtifactKey key, String version) {
            return new ExtensionStatus(key + ":" + version, STATUS_CLASSPATH_UPDATE);
        }

        public ExtensionStatus() {
        }

        public ExtensionStatus(String artifact, String status) {
            this.artifact = artifact;
            this.status = status;
        }

        public String artifact;
        public String status;
    }

    private static Set<ArtifactCoords> getClasspath(Artifact a, List<Dependency> managedDeps, MavenArtifactResolver resolver) {
        final DependencyNode root;
        try {
            root = resolver.collectManagedDependencies(a, List.of(), managedDeps, List.of(), List.of()).getRoot();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException(e);
        }
        return getClasspath(root);
    }

    private static Set<ArtifactCoords> getClasspath(DependencyNode node) {
        var result = new HashSet<ArtifactCoords>();
        for (DependencyNode c : node.getChildren()) {
            collectClasspath(c, result);
        }
        return result;
    }

    private static void collectClasspath(DependencyNode node, Set<ArtifactCoords> collected) {
        var a = node.getArtifact();
        if (a != null) {
            collected.add(
                    ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion()));
        }
        for (DependencyNode c : node.getChildren()) {
            collectClasspath(c, collected);
        }
    }

    private List<Dependency> getPlatformConstraints(ExtensionCatalog catalog, MavenArtifactResolver resolver) {
        Map<String, ?> map = (Map<String, ?>) catalog.getMetadata().get("platform-release");
        if (map == null) {
            throw new IllegalArgumentException("Failed to locate platform-release metadata in " + catalog.getId());
        }
        List<String> members = (List<String>) map.get("members");
        if (members == null) {
            throw new IllegalArgumentException(
                    "Failed to locate members under platform-release metadata in " + catalog.getId());
        }
        List<Dependency> result = new ArrayList<>();
        for (String s : members) {
            var bomCoords = PlatformArtifacts.ensureBomArtifact(ArtifactCoords.fromString(s));
            final ArtifactDescriptorResult descriptor;
            try {
                descriptor = resolver.resolveDescriptor(new DefaultArtifact(bomCoords.getGroupId(), bomCoords.getArtifactId(),
                        bomCoords.getClassifier(), bomCoords.getType(), bomCoords.getVersion()));
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to resolve artifact descriptor for " + bomCoords.toCompactCoords(), e);
            }
            if (descriptor.getManagedDependencies().isEmpty()) {
                getLog().warn("Failed to collect managed dependencies from " + bomCoords.toCompactCoords());
                continue;
            }
            result.addAll(descriptor.getManagedDependencies());
        }
        return result;
    }

    private static Map<ArtifactKey, String> getExtensions(ExtensionCatalog catalog) {
        if (catalog == null) {
            return Map.of();
        }
        var extensionList = catalog.getExtensions();
        final Map<ArtifactKey, String> result = new HashMap<>(extensionList.size());
        for (var e : extensionList) {
            var unlisted = e.getMetadata().get("unlisted");
            if (unlisted != null && ((unlisted instanceof Boolean && (Boolean) unlisted)
                    || (unlisted instanceof String && (Boolean.parseBoolean((String) unlisted))))) {
                continue;
            }
            result.put(e.getArtifact().getKey(), e.getArtifact().getVersion());
        }
        return result;
    }

    private static ExtensionCatalog resolveExtensionCatalog(ArtifactCoords catalogCoords, MavenArtifactResolver resolver)
            throws MojoExecutionException {
        final Path catalogJson;
        try {
            catalogJson = resolver.resolve(new DefaultArtifact(
                    catalogCoords.getGroupId(),
                    catalogCoords.getArtifactId(),
                    catalogCoords.getClassifier(),
                    catalogCoords.getType(),
                    catalogCoords.getVersion())).getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve extension catalog " + catalogCoords, e);
        }

        final ExtensionCatalog catalog;
        try {
            catalog = ExtensionCatalog.fromFile(catalogJson);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to deserialize extension catalog " + catalogJson, e);
        }
        return catalog;
    }

    private ArtifactCoords getPreviousBom(MavenArtifactResolver resolver, ExtensionCatalog catalog)
            throws MojoExecutionException {

        var extResolverBuilder = ExtensionCatalogResolver.builder()
                .artifactResolver(resolver)
                .messageWriter(new MojoMessageWriter(getLog()));
        if (registryConfig != null) {
            if (!registryConfig.exists()) {
                throw new MojoExecutionException(registryConfig + " does not exist");
            }
            if (registryConfig.isDirectory()) {
                throw new MojoExecutionException(registryConfig + " is a directory");
            }
            try {
                extResolverBuilder.config(RegistriesConfig.fromFile(registryConfig.toPath()));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to parse registry configuration file " + registryConfig, e);
            }
        } else {
            if (registry == null || registry.isEmpty()) {
                throw new MojoExecutionException("Registry ID has not been provided");
            }
            extResolverBuilder.config(RegistriesConfig.builder()
                    .setRegistries(List.of(
                            RegistryConfig.builder()
                                    .setId(registry)
                                    .build()))
                    .build());

        }

        final ExtensionCatalogResolver catalogResolver;
        try {
            catalogResolver = extResolverBuilder.build();
        } catch (RegistryResolutionException e) {
            throw new MojoExecutionException("Failed to initialize extension catalog resolver for registry " + registry, e);
        }

        final Map<String, String> platformRelease = ((Map<String, String>) catalog.getMetadata().get("platform-release"));
        if (platformRelease == null) {
            throw new MojoExecutionException("Failed to locate platform-release information in " + catalog.getId());
        }
        var currentPlatform = platformRelease.get("platform-key");
        if (currentPlatform == null) {
            throw new MojoExecutionException("Failed to locate the platform key in " + catalog.getBom().toCompactCoords());
        }
        var currentStream = platformRelease.get("stream");
        if (currentStream == null) {
            throw new MojoExecutionException(
                    "Failed to determine the platform release stream of " + catalog.getBom().toCompactCoords());
        }
        var currentVersion = platformRelease.get("version");
        if (currentVersion == null) {
            throw new MojoExecutionException("Failed to platform release version in " + catalog.getBom().toCompactCoords());
        }
        PlatformStream prevStream;
        try {
            prevStream = findPlatformStream(currentPlatform, currentStream, catalogResolver.resolvePlatformCatalog("all"));
            if (prevStream == null) {
                // the current stream is new
                getLog().debug("No previous platform release stream");
                return null;
            }
        } catch (RegistryResolutionException e) {
            throw new MojoExecutionException("Failed to resolve stream " + currentPlatform + ":" + currentStream, e);
        }

        final GenericVersionScheme versionScheme = new GenericVersionScheme();
        final Version current;
        try {
            current = versionScheme.parseVersion(currentVersion);
        } catch (InvalidVersionSpecificationException e) {
            throw new MojoExecutionException(e);
        }

        Version previous = null;
        PlatformRelease previousRelease = null;
        for (var pr : prevStream.getReleases()) {
            final Version v;
            try {
                v = versionScheme.parseVersion(pr.getVersion().toString());
            } catch (InvalidVersionSpecificationException e) {
                throw new MojoExecutionException(e);
            }
            if (current.compareTo(v) > 0
                    && (previous == null || previous.compareTo(v) < 0)) {
                previous = v;
                previousRelease = pr;
            }
        }
        if (previousRelease == null) {
            return null;
        }

        var bomKey = catalog.getBom().getKey();
        for (var bomCoords : previousRelease.getMemberBoms()) {
            if (bomCoords.getKey().equals(bomKey)) {
                return PlatformArtifacts.ensureCatalogArtifact(bomCoords);
            }
        }
        return null;
    }

    private static PlatformStream findPlatformStream(String platformKey, String streamId, PlatformCatalog platformCatalog) {
        for (var platform : platformCatalog.getPlatforms()) {
            if (platformKey.equals(platform.getPlatformKey())) {
                var prevStream = platform.getStream(streamId);
                if (prevStream != null) {
                    return prevStream;
                }
            }
        }
        return null;
    }
}
