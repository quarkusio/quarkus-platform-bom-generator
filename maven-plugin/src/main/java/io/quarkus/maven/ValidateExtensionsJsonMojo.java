package io.quarkus.maven;

import io.quarkus.bom.decomposer.maven.QuarkusWorkspaceProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.paths.PathTree;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * This goal validates a given JSON descriptor.
 * Specifically, it will make sure that all the extensions that are included in the BOM
 * the catalog is referencing that are expected to be in the catalog are
 * actually present in the catalog. And that all the extensions that are found
 * in the catalog are also present in the BOM the descriptor is referencing.
 *
 */
@Mojo(name = "validate-extension-catalog", threadSafe = true)
public class ValidateExtensionsJsonMojo extends AbstractMojo {

    @Component
    RepositorySystem repoSystem;

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(property = "jsonGroupId", required = true)
    private String jsonGroupId;

    @Parameter(property = "jsonArtifactId", required = true)
    private String jsonArtifactId;

    @Parameter(property = "jsonVersion", required = true)
    private String jsonVersion;

    /**
     * Skip the execution of this mojo.
     *
     * @since 1.4.0
     */
    @Parameter(defaultValue = "false", property = "quarkus.validate-extensions-json.skip")
    private boolean skip;

    /**
     * Group ID's that we know don't contain extensions. This can speed up the process
     * by preventing the download of artifacts that are not required.
     */
    @Parameter
    private Set<String> ignoredGroupIds = new HashSet<>(0);

    @Component
    QuarkusWorkspaceProvider bootstrapProvider;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping as required by the mojo configuration");
            return;
        }

        MavenArtifactResolver mvn;
        try {
            mvn = MavenArtifactResolver.builder()
                    .setRepositorySystem(repoSystem)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .build();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to initialize maven artifact resolver", e);
        }

        final Artifact artifact = new DefaultArtifact(jsonGroupId, jsonArtifactId, jsonVersion, "json", jsonVersion);
        final Path jsonPath;
        try {
            jsonPath = mvn.resolve(artifact).getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve platform descriptor " + artifact, e);
        }

        ExtensionCatalog catalog;
        try {
            catalog = ExtensionCatalog.fromFile(jsonPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to deserialize extension catalog " + jsonPath, e);
        }
        final ArtifactCoords bomCoords = catalog.getBom();

        final DefaultArtifact bomArtifact = new DefaultArtifact(bomCoords.getGroupId(),
                bomCoords.getArtifactId(), bomCoords.getClassifier(), bomCoords.getType(), bomCoords.getVersion());
        final Map<ArtifactKey, ArtifactCoords> bomExtensionArtifacts = collectBomExtensionArtifacts(mvn, bomArtifact);

        List<ArtifactCoords> missingFromBom = new ArrayList<>();
        for (Extension ext : catalog.getExtensions()) {
            ArtifactCoords rtCoords = ext.getArtifact();
            if (bomExtensionArtifacts.remove(rtCoords.getKey()) == null) {
                missingFromBom.add(rtCoords);
            }

            ArtifactKey deploymentKey = ArtifactKey.of(rtCoords.getGroupId(), rtCoords.getArtifactId() + "-deployment",
                    rtCoords.getClassifier(), rtCoords.getType());
            if (bomExtensionArtifacts.remove(deploymentKey) == null) {
                final Path rtJar;
                final Artifact rtArtifact = new DefaultArtifact(rtCoords.getGroupId(), rtCoords.getArtifactId(),
                        rtCoords.getClassifier(), rtCoords.getType(), rtCoords.getVersion());
                try {
                    rtJar = mvn.resolve(rtArtifact).getArtifact().getFile().toPath();
                } catch (BootstrapMavenException e) {
                    throw new MojoExecutionException("Failed to resolve " + rtCoords, e);
                }
                final ArtifactCoords deploymentCoords = PathTree.ofDirectoryOrArchive(rtJar)
                        .apply(BootstrapConstants.DESCRIPTOR_PATH, visit -> {
                            if (visit == null) {
                                return null;
                            }
                            return readDeploymentCoords(visit.getPath(), rtArtifact);
                        });
                if (deploymentCoords == null) {
                    throw new MojoExecutionException(
                            "Failed to determine the corresponding deployment artifact for " + rtCoords.toCompactCoords());
                }
                if (bomExtensionArtifacts.remove(deploymentCoords.getKey()) == null) {
                    missingFromBom.add(deploymentCoords);
                }
            }
        }

        if (bomExtensionArtifacts.isEmpty() && missingFromBom.isEmpty()) {
            return;
        }

        if (!bomExtensionArtifacts.isEmpty()) {
            getLog().error("Extensions from " + bomArtifact + " mising from " + artifact);
            for (Map.Entry<ArtifactKey, ArtifactCoords> entry : bomExtensionArtifacts.entrySet()) {
                getLog().error("- " + entry.getValue().toCompactCoords());
            }
        }
        if (!missingFromBom.isEmpty()) {
            getLog().error("Extension artifacts from " + artifact + " missing from " + bomArtifact);
            for (ArtifactCoords coords : missingFromBom) {
                getLog().error("- " + coords);
            }
        }
        throw new MojoExecutionException("Extensions referenced in " + bomArtifact + " and included in " + artifact
                + " do not match expectations. See the errors logged above.");
    }

    private Map<ArtifactKey, ArtifactCoords> collectBomExtensionArtifacts(MavenArtifactResolver mvn,
            DefaultArtifact platformBom)
            throws MojoExecutionException {

        final List<Dependency> bomDeps;
        try {
            bomDeps = mvn.resolveDescriptor(platformBom).getManagedDependencies();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve platform BOM " + platformBom, e);
        }

        final Map<ArtifactKey, ArtifactCoords> bomExtensions = new HashMap<>(bomDeps.size());

        for (Dependency dep : bomDeps) {
            final Artifact artifact = dep.getArtifact();
            if (ignoredGroupIds.contains(artifact.getGroupId())
                    || !artifact.getExtension().equals(ArtifactCoords.TYPE_JAR)
                    || "javadoc".equals(artifact.getClassifier())
                    || "tests".equals(artifact.getClassifier())
                    || "sources".equals(artifact.getClassifier())) {
                continue;
            }
            final Artifact resolvedArtifact;
            try {
                resolvedArtifact = mvn.resolve(artifact).getArtifact();
            } catch (Exception e) {
                getLog().warn(
                        "Failed to resolve " + artifact + " present in the dependencyManagement section of " + platformBom);
                continue;
            }
            analyzeArtifact(resolvedArtifact, bomExtensions);
        }
        return bomExtensions;
    }

    private void analyzeArtifact(Artifact artifact, Map<ArtifactKey, ArtifactCoords> extensions) throws MojoExecutionException {
        final Path path = artifact.getFile().toPath();
        if (!Files.exists(path)) {
            throw new MojoExecutionException("Failed to locate " + artifact + " at " + path);
        }

        final PathTree content = PathTree.ofDirectoryOrArchive(path);
        content.accept("META-INF", visit -> {
            if (visit == null) {
                return;
            }

            final Path metaInf = visit.getPath();
            final Path descrProps = metaInf.resolve(BootstrapConstants.DESCRIPTOR_FILE_NAME);
            final Path descrYaml = metaInf.resolve(BootstrapConstants.QUARKUS_EXTENSION_FILE_NAME);
            final Path descrJson = metaInf.resolve(BootstrapConstants.EXTENSION_PROPS_JSON_FILE_NAME);
            if (ensureExtensionMetadata(artifact, descrProps, descrYaml, descrJson)) {
                final ArtifactCoords deployment = readDeploymentCoords(descrProps, artifact);
                final ArtifactKey key = ArtifactKey.of(artifact.getGroupId(), artifact.getArtifactId(),
                        artifact.getClassifier(),
                        artifact.getExtension());
                extensions.put(key, ArtifactCoords.of(key.getGroupId(), key.getArtifactId(), key.getClassifier(), key.getType(),
                        artifact.getVersion()));
                extensions.put(deployment.getKey(), deployment);
            }
        });
    }

    private ArtifactCoords readDeploymentCoords(final Path descrProps, Artifact artifact) {
        final Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(descrProps)) {
            props.load(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + descrProps + " from " + artifact);
        }
        final String s = props.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
        if (s == null) {
            throw new RuntimeException(
                    artifact + " is missing " + BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT + " property in " + descrProps);
        }
        final ArtifactCoords deployment = ArtifactCoords.fromString(s);
        return deployment;
    }

    private static boolean ensureExtensionMetadata(Artifact a, Path properties, Path yaml, Path json) {
        final boolean propsExist = Files.exists(properties);
        final boolean metadataExists = Files.exists(yaml) || Files.exists(json);
        if (propsExist == metadataExists) {
            return propsExist;
        }
        if (metadataExists) {
            throw new RuntimeException(a + " includes Quarkus extension metadata but not " + properties);
        }
        throw new RuntimeException(
                a + " includes Quarkus extension properties but not " + BootstrapConstants.EXTENSION_METADATA_PATH);
    }
}
