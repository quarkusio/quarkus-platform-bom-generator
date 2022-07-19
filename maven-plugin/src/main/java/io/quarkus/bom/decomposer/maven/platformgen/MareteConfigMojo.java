package io.quarkus.bom.decomposer.maven.platformgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.bom.platform.PlatformMemberConfig;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate-marete-config", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE, requiresProject = false)
public class MareteConfigMojo extends AbstractMojo {

    @Parameter(required = true, defaultValue = "${basedir}/src/main/resources/core/marete-template.yaml")
    File configTemplate;

    @Parameter(required = true, defaultValue = "${project.build.directory}/marete-config.yaml")
    File generatedConfig;

    @Parameter
    PlatformConfig platformConfig;

    private ObjectMapper mapper = CatalogMapperHelper.initMapper(new ObjectMapper(new YAMLFactory()));

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final JsonNode mareteConfig = readMareteTemplate();
        final JsonNode mavenRepo = getRequiredNode(mareteConfig, "maven-repo");

        final List<ArtifactKey> generatedBoms = getGeneratedBoms();

        curateExpectedBoms(mavenRepo, generatedBoms);
        curateUnexpectedFilesExceptions(mavenRepo, generatedBoms);

        persistMareteConfig(mareteConfig);
    }

    private void curateUnexpectedFilesExceptions(final JsonNode mavenRepo, Collection<ArtifactKey> generatedBoms)
            throws MojoExecutionException {
        final String unexpectedFilesExceptionsFieldName = "unexpected-files-exceptions";

        JsonNode node = mavenRepo.get(unexpectedFilesExceptionsFieldName);
        final ArrayNode unexpectedFiles;
        final Set<String> configuredUnexpectedFiles;
        if (node != null && !(node instanceof NullNode)) {
            if (!(node instanceof ArrayNode)) {
                throw new MojoExecutionException(
                        unexpectedFilesExceptionsFieldName + " is not an instance of " + ArrayNode.class.getName() + " but "
                                + node.getClass().getName());
            }
            unexpectedFiles = (ArrayNode) node;
            configuredUnexpectedFiles = new HashSet<>(unexpectedFiles.size());
            for (int i = 0; i < unexpectedFiles.size(); ++i) {
                final JsonNode jsonNode = unexpectedFiles.get(i);
                if (!JsonNodeType.STRING.equals(jsonNode.getNodeType())) {
                    throw new MojoExecutionException("Unexpected file is not a STRING type but " + jsonNode.getNodeType());
                }
                configuredUnexpectedFiles.add(jsonNode.asText());
            }
        } else {
            unexpectedFiles = mapper.createArrayNode();
            ((ObjectNode) mavenRepo).set(unexpectedFilesExceptionsFieldName, unexpectedFiles);
            configuredUnexpectedFiles = Set.of();
        }

        for (ArtifactKey generatedBom : generatedBoms) {
            String fileName = generatedBom.getArtifactId() + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX
                    + "-*.json";
            if (!configuredUnexpectedFiles.contains(fileName)) {
                unexpectedFiles.add(fileName);
            }
            fileName = generatedBom.getArtifactId() + BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX
                    + "-*.properties";
            if (!configuredUnexpectedFiles.contains(fileName)) {
                unexpectedFiles.add(fileName);
            }
        }
    }

    private void curateExpectedBoms(final JsonNode mavenRepo, Collection<ArtifactKey> generatedBoms)
            throws MojoExecutionException {
        final String expectedBomsFielName = "expected-boms";

        JsonNode node = mavenRepo.get(expectedBomsFielName);
        final ArrayNode expectedBoms;
        final Set<ArtifactKey> configuredExpectedBoms;
        if (node != null && !(node instanceof NullNode)) {
            if (!(node instanceof ArrayNode)) {
                throw new MojoExecutionException(
                        expectedBomsFielName + " is not an instance of " + ArrayNode.class.getName() + " but "
                                + node.getClass().getName());
            }
            expectedBoms = (ArrayNode) node;
            configuredExpectedBoms = new HashSet<>(expectedBoms.size());
            for (int i = 0; i < expectedBoms.size(); ++i) {
                final JsonNode jsonNode = expectedBoms.get(i);
                if (!JsonNodeType.STRING.equals(jsonNode.getNodeType())) {
                    throw new MojoExecutionException("Expected BOM is not a STRING type but " + jsonNode.getNodeType());
                }
                final ArtifactKey key = ArtifactKey.fromString(jsonNode.asText());
                configuredExpectedBoms.add(ArtifactKey.ga(key.getGroupId(), key.getArtifactId()));
            }
        } else {
            expectedBoms = mapper.createArrayNode();
            ((ObjectNode) mavenRepo).set(expectedBomsFielName, expectedBoms);
            configuredExpectedBoms = Set.of();
        }

        for (ArtifactKey generatedBom : generatedBoms) {
            if (!configuredExpectedBoms.contains(generatedBom)) {
                expectedBoms.add(generatedBom.getGroupId() + ":" + generatedBom.getArtifactId());
            }
        }
    }

    private static JsonNode getRequiredNode(JsonNode parent, String name) throws MojoExecutionException {
        final JsonNode node = parent.get(name);
        if (node == null) {
            throw new MojoExecutionException("Failed to find " + name + " in the template");
        }
        return node;
    }

    private void persistMareteConfig(final JsonNode mareteConfig) throws MojoExecutionException {
        if (!generatedConfig.exists()) {
            generatedConfig.getParentFile().mkdirs();
        }
        try {
            mapper.writer().writeValue(generatedConfig, mareteConfig);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write to " + generatedConfig, e);
        }
    }

    private JsonNode readMareteTemplate() throws MojoExecutionException {
        try (BufferedReader reader = Files.newBufferedReader(configTemplate.toPath())) {
            return mapper.readTree(reader);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + configTemplate, e);
        }
    }

    private List<ArtifactKey> getGeneratedBoms() {
        final List<ArtifactKey> expectedBoms = new ArrayList<>();
        final ArtifactCoords universeBom = ArtifactCoords.fromString(platformConfig.getUniversal().getBom());
        expectedBoms.add(ga(universeBom));
        expectedBoms.add(ga(ArtifactCoords.fromString(platformConfig.getCore().getBom())));
        expectedBoms.add(ga(platformConfig.getCore().getGeneratedBom(universeBom.getGroupId())));
        for (PlatformMemberConfig member : platformConfig.getMembers()) {
            if (!member.isEnabled() || member.isHidden()) {
                continue;
            }
            expectedBoms.add(ga(member.getGeneratedBom(universeBom.getGroupId())));
        }
        return expectedBoms;
    }

    private ArtifactKey ga(ArtifactCoords coords) {
        return ArtifactKey.ga(coords.getGroupId(), coords.getArtifactId());
    }
}
