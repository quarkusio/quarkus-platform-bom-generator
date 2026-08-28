package io.quarkus.platform.generator.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.bom.decomposer.maven.platformgen.CpeArtifactsEncoder;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.platform.generator.PlatformTestProjectGenerator;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MemberCpeArtifactsPropertyTest {

    private static final String CAMEL_CPE = "cpe:2.3:a:acme:camel_quarkus:1.0:*:*:*:*:*:*:*";

    @TempDir
    Path workingDir;

    @Test
    void memberCpeArtifactsPropertyIsGeneratedWhenOfferingIsSet() throws Exception {
        var platformGenerator = PlatformTestProjectGenerator.newInstance();

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom");
        var camelAtomDeployment = camelProject.addModule("camel-atom-deployment")
                .addDependency(camelAtom);
        var camelBom = camelProject.addPomModule("camel-bom")
                .addVersionConstraint(camelAtom)
                .addVersionConstraint(camelAtomDeployment);

        platformGenerator.configureMember(camelBom)
                .setName("Camel")
                .setCpe(CAMEL_CPE)
                .setOffering("camel")
                .addExtensionMetadata(
                        ArtifactCoords.jar(camelAtom.getGroupId(), camelAtom.getArtifactId(), camelAtom.getVersion()),
                        "camel-support", true);

        var platform = platformGenerator
                .setProjectDir(workingDir)
                .generateProject()
                .build();

        var camel = platform.getMember("Camel");
        var cpeArtifactsKey = "platform." + PlatformTestProjectGenerator.DEFAULT_GROUP_ID
                + ".quarkus-camel-bom.cpe-artifacts";

        assertThat(camel.getProperties()).containsKey(cpeArtifactsKey);

        String encoded = camel.getProperties().getProperty(cpeArtifactsKey);
        Map<ArtifactCoords, List<ArtifactCoords>> decoded = CpeArtifactsEncoder.decode(encoded);
        assertThat(decoded).isNotEmpty();

        var keyPrefix = "platform." + PlatformTestProjectGenerator.DEFAULT_GROUP_ID + ".quarkus-camel-bom.";
        assertThat(camel.getProperties().getProperty(keyPrefix + "product-name")).isEqualTo("Camel");

        // the map is keyed by the runtime extension artifact
        var runtimeKey = decoded.keySet().stream()
                .filter(coords -> coords.getArtifactId().equals("camel-atom"))
                .findFirst()
                .orElse(null);
        assertThat(runtimeKey)
                .as("Decoded map should be keyed by the camel-atom runtime artifact")
                .isNotNull();

        // the value should contain the corresponding deployment artifact
        assertThat(decoded.get(runtimeKey).stream()
                .anyMatch(coords -> coords.getArtifactId().equals("camel-atom-deployment")))
                        .as("The runtime artifact should map to the camel-atom-deployment artifact")
                        .isTrue();
    }

    @Test
    void memberWithoutOfferingDoesNotGenerateCpeArtifacts() throws Exception {
        var platformGenerator = PlatformTestProjectGenerator.newInstance();

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom");
        var camelBom = camelProject.addPomModule("camel-bom")
                .addVersionConstraint(camelAtom);

        platformGenerator.configureMember(camelBom)
                .setName("Camel")
                .setCpe(CAMEL_CPE);

        var platform = platformGenerator
                .setProjectDir(workingDir)
                .generateProject()
                .build();

        var camel = platform.getMember("Camel");
        assertThat(camel.getProperties().stringPropertyNames())
                .noneMatch(name -> name.endsWith(".cpe-artifacts"));
    }
}
