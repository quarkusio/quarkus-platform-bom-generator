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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate-marete-config", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE, requiresProject = false)
public class MareteConfigMojo extends AbstractMojo {

    private static final String DEPS_TO_BUILD_REPORT_SUFFIX = "-deps-to-build.txt";

    @Parameter(required = true, defaultValue = "${basedir}/src/main/resources/core/marete-template.yaml")
    File configTemplate;

    @Parameter(required = true, defaultValue = "${project.build.directory}/marete-config.yaml")
    File generatedConfig;

    @Parameter
    PlatformConfig platformConfig;

    @Parameter(required = true, defaultValue = "${project.build.directory}/dependencies-to-build")
    File depsToBuildReportDir;

    @Parameter(required = true, defaultValue = "true", property = "curateExpectedBoms")
    boolean curateExpectedBoms = true;
    @Parameter(required = true, defaultValue = "true", property = "curateUnexpectedFilesExceptions")
    boolean curateUnexpectedFilesExceptions = true;
    @Parameter(required = true, defaultValue = "true", property = "curateUniqueArtifactsExceptions")
    boolean curateUniqueArtifactsExceptions = true;

    private ObjectMapper mapper = CatalogMapperHelper.initMapper(new ObjectMapper(new YAMLFactory()));
    private Map<ArtifactKey, Set<String>> artifactVersions;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final JsonNode mareteConfig = readMareteTemplate();
        final JsonNode mavenRepo = getRequiredNode(mareteConfig, "maven-repo");

        final List<ArtifactKey> generatedBoms = getGeneratedBoms();

        if (curateExpectedBoms) {
            curateExpectedBoms(mavenRepo, generatedBoms);
        }
        if (curateUnexpectedFilesExceptions) {
            curateUnexpectedFilesExceptions(mavenRepo, generatedBoms);
        }
        if (curateUniqueArtifactsExceptions) {
            curateUniqueArtifactsException(mavenRepo);
        }

        persistMareteConfig(mareteConfig);
    }

    private Map<ArtifactKey, Set<String>> getArtifactVersions() throws MojoExecutionException {
        if (artifactVersions != null) {
            return artifactVersions;
        }
        if (!depsToBuildReportDir.exists()) {
            getLog().warn("Failed to locate dependencies-to-build reports at " + depsToBuildReportDir);
            return artifactVersions = Map.of();
        }
        final Map<ArtifactKey, Set<String>> artifactVersions = new HashMap<>();
        try (Stream<Path> stream = Files.list(depsToBuildReportDir.toPath())) {
            var i = stream.iterator();
            while (i.hasNext()) {
                var p = i.next();
                if (!p.getFileName().toString().endsWith(DEPS_TO_BUILD_REPORT_SUFFIX)) {
                    continue;
                }
                try (Stream<String> ls = Files.lines(p)) {
                    var li = ls.iterator();
                    while (li.hasNext()) {
                        var s = li.next();
                        if (s.isBlank() || s.charAt(0) == '#') {
                            continue;
                        }
                        var coords = ArtifactCoords.fromString(s);
                        artifactVersions.computeIfAbsent(coords.getKey(), k -> new HashSet<>()).add(coords.getVersion());
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read dependencies-to-build reports", e);
        }
        return this.artifactVersions = artifactVersions;
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
        final Set<ArtifactKey> configuredExpectedBoms = new HashSet<>();
        final ArrayNode expectedBoms = getArtifactKeyArray(mavenRepo, "expected-boms", configuredExpectedBoms);
        for (ArtifactKey generatedBom : generatedBoms) {
            if (!configuredExpectedBoms.contains(generatedBom)) {
                expectedBoms.add(generatedBom.getGroupId() + ":" + generatedBom.getArtifactId());
            }
        }
    }

    private void curateUniqueArtifactsException(final JsonNode mavenRepo)
            throws MojoExecutionException {
        final TreeMap<String, ArtifactKey> foundExceptions = new TreeMap<>();
        for (Map.Entry<ArtifactKey, Set<String>> a : getArtifactVersions().entrySet()) {
            if (a.getValue().size() > 1) {
                final ArtifactKey key = a.getKey();
                var s = key.getGroupId() + ':' + key.getArtifactId();
                final String classifier = key.getClassifier();
                if (classifier != null && !classifier.isBlank()) {
                    s += ':' + classifier;
                }
                foundExceptions.put(s, key);
            }
        }
        final Set<ArtifactKey> configuredKeys = new HashSet<>();
        final ArrayNode array = getArtifactKeyArray(mavenRepo, "unique-artifacts-exceptions", configuredKeys);
        for (Map.Entry<String, ArtifactKey> e : foundExceptions.entrySet()) {
            if (!configuredKeys.contains(e.getValue())) {
                array.add(e.getKey());
            }
        }
    }

    private ArrayNode getArtifactKeyArray(final JsonNode mavenRepo, final String name,
            final Set<ArtifactKey> collectedItems) throws MojoExecutionException {
        JsonNode node = mavenRepo.get(name);
        final ArrayNode expectedBoms;
        if (node != null && !(node instanceof NullNode)) {
            if (!(node instanceof ArrayNode)) {
                throw new MojoExecutionException(
                        name + " is not an instance of " + ArrayNode.class.getName() + " but "
                                + node.getClass().getName());
            }
            expectedBoms = (ArrayNode) node;
            for (int i = 0; i < expectedBoms.size(); ++i) {
                final JsonNode jsonNode = expectedBoms.get(i);
                if (!JsonNodeType.STRING.equals(jsonNode.getNodeType())) {
                    throw new MojoExecutionException("Expected item is not a STRING type but " + jsonNode.getNodeType());
                }
                final ArtifactKey key = ArtifactKey.fromString(jsonNode.asText());
                collectedItems.add(ArtifactKey.ga(key.getGroupId(), key.getArtifactId()));
            }
        } else {
            expectedBoms = mapper.createArrayNode();
            ((ObjectNode) mavenRepo).set(name, expectedBoms);
        }
        return expectedBoms;
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
        expectedBoms.add(ga(platformConfig.getCore().getPlatformBom(universeBom.getGroupId())));
        for (PlatformMemberConfig member : platformConfig.getMembers()) {
            if (!member.isEnabled() || member.isHidden()) {
                continue;
            }
            expectedBoms.add(ga(member.getPlatformBom(universeBom.getGroupId())));
        }
        return expectedBoms;
    }

    private ArtifactKey ga(ArtifactCoords coords) {
        return ArtifactKey.ga(coords.getGroupId(), coords.getArtifactId());
    }
}
