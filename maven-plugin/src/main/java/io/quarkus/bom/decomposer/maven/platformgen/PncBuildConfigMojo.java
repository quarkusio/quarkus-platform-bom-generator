package io.quarkus.bom.decomposer.maven.platformgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.bom.platform.DependenciesToBuildConfig;
import io.quarkus.bom.platform.PlatformMemberConfig;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Supplier;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate-pnc-build-config", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE, requiresProject = false)
public class PncBuildConfigMojo extends AbstractMojo {

    private static final String BOM_GAVS = "bomGavs";
    private static final String PARAMETERS = "parameters";
    private static final String STEPS = "steps";

    @Parameter(required = true, defaultValue = "${basedir}/src/main/resources/build-config-template.yaml")
    File configTemplate;

    @Parameter(required = true, defaultValue = "${project.build.directory}/build-config.yaml")
    File generatedConfig;

    @Parameter
    PlatformConfig platformConfig;

    private ObjectMapper mapper = CatalogMapperHelper.initMapper(new ObjectMapper(new YAMLFactory()));

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final JsonNode buildConfig = readBuildConfigTemplate();
        setIfNotConfigured(buildConfig, "version", platformConfig.getRelease().getVersion());

        final JsonNode repositoryGeneration = getOrCreateNode(buildConfig, "flow", "repositoryGeneration");
        configureDefaultRepoParams(repositoryGeneration);
        addMemberRepoGeneratingSteps(repositoryGeneration);

        persistBuildConfig(buildConfig);
    }

    private void configureDefaultRepoParams(final JsonNode repositoryGeneration) throws MojoExecutionException {
        final JsonNode defaultParameters = getOrCreateNode(repositoryGeneration, PARAMETERS);
        setIfNotConfigured(defaultParameters, BOM_GAVS, () -> {
            var c = platformConfig.getCore().getGeneratedBom(platformConfig.getRelease().getPlatformKey());
            return c.getGroupId() + ':' + c.getArtifactId() + ':' + c.getVersion();
        });
        setIfNotConfigured(defaultParameters, "resolveIncludes", "*:*:*redhat-*");

        final DependenciesToBuildConfig coreDepsToBuild = platformConfig.getCore().getDependenciesToBuild();
        if (coreDepsToBuild != null && !coreDepsToBuild.getIncludeArtifacts().isEmpty()) {
            final ArrayNode steps = getOrCreateArray(repositoryGeneration, STEPS);
            JsonNode coreStep = null;
            for (int i = 0; i < steps.size(); ++i) {
                JsonNode node = steps.get(i);
                if (!node.has(PARAMETERS)) {
                    coreStep = node;
                    break;
                }
                final JsonNode params = node.get(PARAMETERS);
                if (!params.has(BOM_GAVS)) {
                    coreStep = node;
                    break;
                }
            }
            if (coreStep == null) {
                coreStep = mapper.createObjectNode();
                steps.add(coreStep);
            }
            addResolveArtifacts(getOrCreateNode(coreStep, PARAMETERS), coreDepsToBuild);
        }
    }

    private void addResolveArtifacts(final JsonNode parameters, final DependenciesToBuildConfig depsToBuild) {
        setIfNotConfigured(parameters, "resolveArtifacts", () -> {
            final Iterator<ArtifactCoords> i = depsToBuild.getIncludeArtifacts().iterator();
            final StringBuilder sb = new StringBuilder().append(toGATCV(i.next()));
            while (i.hasNext()) {
                sb.append(", ").append(toGATCV(i.next()));
            }
            return sb.toString();
        });
    }

    private void addMemberRepoGeneratingSteps(JsonNode repositoryGeneration) throws MojoExecutionException {
        if (platformConfig.getMembers().isEmpty()) {
            return;
        }
        final ArrayNode steps = getOrCreateArray(repositoryGeneration, STEPS);
        final String defaultGroupId = platformConfig.getRelease().getPlatformKey();
        for (PlatformMemberConfig member : platformConfig.getMembers()) {
            if (!member.isEnabled() || member.isHidden()) {
                continue;
            }
            final ArtifactCoords memberBom = member.getGeneratedBom(defaultGroupId);
            final JsonNode step = getOrCreateItemWithElement(steps,
                    memberBom.getGroupId() + ':' + memberBom.getArtifactId() + ':' + memberBom.getVersion(),
                    PARAMETERS, BOM_GAVS);

            if (member.getDependenciesToBuild() != null && !member.getDependenciesToBuild().getIncludeArtifacts().isEmpty()) {
                addResolveArtifacts(getOrCreateNode(step, PARAMETERS), member.getDependenciesToBuild());
            }
        }
    }

    private static String toGATCV(ArtifactCoords c) {
        return c.getGroupId() + ':' + c.getArtifactId() + ':' + c.getType() + ':' + c.getClassifier() + ':' + c.getVersion();
    }

    private JsonNode getOrCreateItemWithElement(ArrayNode array, String value, String... fieldName) {
        for (int i = 0; i < array.size(); ++i) {
            final JsonNode node = array.get(i);
            JsonNode idNode = node;
            for (String name : fieldName) {
                idNode = ((ObjectNode) idNode).get(name);
                if (idNode == null) {
                    break;
                }
            }
            if (idNode != null && value.equals(idNode.textValue())) {
                return node;
            }
        }
        final ObjectNode root = mapper.createObjectNode();
        ObjectNode node = root;
        for (int i = 0; i < fieldName.length - 1; ++i) {
            final ObjectNode child = mapper.createObjectNode();
            node.set(fieldName[i], child);
            node = child;
        }
        node.set(fieldName[fieldName.length - 1], mapper.getNodeFactory().textNode(value));
        array.add(root);
        return root;
    }

    private ArrayNode getOrCreateArray(JsonNode parent, String... name) throws MojoExecutionException {
        if (name.length > 1) {
            parent = getOrCreateNode(parent, Arrays.copyOfRange(name, 0, name.length - 1));
        }
        JsonNode node = parent.get(name[name.length - 1]);
        final ArrayNode array;
        if (node != null && !(node instanceof NullNode)) {
            if (!(node instanceof ArrayNode)) {
                throw new MojoExecutionException(
                        name[name.length] + " is not an instance of " + ArrayNode.class.getName() + " but "
                                + node.getClass().getName());
            }
            array = (ArrayNode) node;
        } else {
            array = mapper.createArrayNode();
            ((ObjectNode) parent).set(name[name.length - 1], array);
        }
        return array;
    }

    private JsonNode getOrCreateNode(JsonNode parent, String... name) throws MojoExecutionException {
        if (name.length == 0) {
            return parent;
        }
        JsonNode node = parent;
        for (String n : name) {
            JsonNode tmp = node.get(n);
            if (tmp == null) {
                tmp = mapper.createObjectNode();
                ((ObjectNode) node).set(n, tmp);
            }
            node = tmp;
        }
        return node;
    }

    private void setIfNotConfigured(final JsonNode node, String fieldName, String value) {
        if (!node.has(fieldName)) {
            ((ObjectNode) node).set(fieldName, mapper.getNodeFactory().textNode(value));
        }
    }

    private void setIfNotConfigured(final JsonNode node, String fieldName, Supplier<String> supplier) {
        if (!node.has(fieldName)) {
            ((ObjectNode) node).set(fieldName, mapper.getNodeFactory().textNode(supplier.get()));
        }
    }

    private void persistBuildConfig(final JsonNode mareteConfig) throws MojoExecutionException {
        if (!generatedConfig.exists()) {
            generatedConfig.getParentFile().mkdirs();
        }
        try {
            mapper.writer().writeValue(generatedConfig, mareteConfig);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write to " + generatedConfig, e);
        }
    }

    private JsonNode readBuildConfigTemplate() throws MojoExecutionException {
        try (BufferedReader reader = Files.newBufferedReader(configTemplate.toPath())) {
            return mapper.readTree(reader);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + configTemplate, e);
        }
    }
}
