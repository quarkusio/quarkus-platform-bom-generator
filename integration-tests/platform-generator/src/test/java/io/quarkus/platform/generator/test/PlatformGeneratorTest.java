package io.quarkus.platform.generator.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.platform.generator.PlatformTestProjectGenerator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PlatformGeneratorTest {

    @TempDir
    Path workingDir;

    @Test
    void test() throws Exception {
        //Path workingDir = Path.of("/home/aloubyansky/playground/test-project");
        //IoUtils.recursiveDelete(workingDir);

        var platformGenerator = PlatformTestProjectGenerator.newInstance();

        var jackson10 = platformGenerator.configureProject("org.jackson", "jackson-parent", "1.0")
                .setScm("https://jackson.org", "1.0");
        var jacksonLibA10 = jackson10.addModule("jackson-lib-a");
        var jacksonLibB10 = jackson10.addModule("jackson-lib-b");
        var jacksonBom10 = jackson10.addPomModule("jackson-bom")
                .addVersionConstraint(jacksonLibA10)
                .addVersionConstraint(jacksonLibB10);

        var jackson20 = platformGenerator.configureProject("org.jackson", "jackson-parent", "2.0")
                .setScm("https://jackson.org", "2.0");
        var jacksonLibA20 = jackson20.addModule("jackson-lib-a");
        var jacksonLibB20 = jackson20.addModule("jackson-lib-b");
        var jacksonBom20 = jackson20.addPomModule("jackson-bom")
                .addVersionConstraint(jacksonLibA20)
                .addVersionConstraint(jacksonLibB20);

        var pets10 = platformGenerator.configureProject("org.pets", "pets-parent", "1.0")
                .setScm("https://pets.org", "1.0");
        var petsCat10 = pets10.addModule("pets-cat");
        var petsDog10 = pets10.addModule("pets-dog");

        var pets20 = platformGenerator.configureProject("org.pets", "pets-parent", "2.0")
                .setScm("https://pets.org", "2.0");
        var petsCat20 = pets20.addModule("pets-cat");
        var petsDog20 = pets20.addModule("pets-dog");

        var quarkusBom = platformGenerator.getQuarkusBom()
                .importBom(jacksonBom10)
                .addVersionConstraint(petsCat10);

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom");
        var camelBom = camelProject.addPomModule("camel-bom")
                .importBom(jacksonBom20)
                .addVersionConstraint(camelAtom)
                .addVersionConstraint(petsDog20);

        var platform = platformGenerator
                .setProjectDir(workingDir)
                .addMember("Camel", camelBom)
                .generateProject()
                .build("process-resources");

        assertThat(platform.getCore()).isNotNull();
        assertThat(platform.getCore().getExtensionCatalog()).isNotNull();
        assertThat(platform.getCore().getBom()).isNotNull();
        assertThat(platform.getCore().containsConstraint(
                ArtifactCoords.jar("io.quarkus", "quarkus-core", platform.getCore().getQuarkusCoreVersion()))).isTrue();
        assertThat(platform.getCore().containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(petsCat10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(petsDog10.getArtifactCoords())).isFalse();

        assertThat(platform.getUniverse()).isNotNull();
        assertThat(platform.getUniverse().getExtensionCatalog()).isNotNull();
        assertThat(platform.getUniverse().getBom()).isNotNull();
        assertThat(platform.getUniverse().containsConstraint(
                ArtifactCoords.jar("io.quarkus", "quarkus-core", platform.getCore().getQuarkusCoreVersion()))).isTrue();
        assertThat(platform.getUniverse().containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(petsCat10.getArtifactCoords())).isTrue();
        // TODO due to the limitation in the LocalWorkspace
        //assertThat(platform.getUniverse().containsConstraint(petsDog10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(petsDog10.getArtifactCoords())).isFalse();
        assertThat(platform.getUniverse().containsConstraint(petsDog20.getArtifactCoords())).isTrue();

        assertThat(platform.getMembers()).hasSize(1);

        var camel = platform.getMember("Camel");
        assertThat(camel).isNotNull();
        assertThat(camel.containsConstraint(camelAtom.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(petsCat10.getArtifactCoords())).isFalse();
        // TODO due to the limitation in the LocalWorkspace
        // assertThat(camel.containsConstraint(petsDog10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(petsDog20.getArtifactCoords())).isTrue();
    }
}
