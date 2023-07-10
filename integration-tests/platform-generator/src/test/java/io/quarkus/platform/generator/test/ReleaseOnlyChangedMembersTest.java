package io.quarkus.platform.generator.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.platform.generator.PlatformTestProjectGenerator;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ReleaseOnlyChangedMembersTest {

    @TempDir
    Path workingDir;

    @Test
    void test() throws Exception {
        //Path workingDir = Path.of("/home/aloubyansky/playground/test-project");
        //IoUtils.recursiveDelete(workingDir);

        var platformGenerator = PlatformTestProjectGenerator.newInstance()
                .setReleaseOnlyChangedMembers(true);

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

        var fruits10 = platformGenerator.configureProject("org.fruits", "fruits-parent", "1.0")
                .setScm("https://fruits.org", "1.0");
        var fruitsApple10 = fruits10.addModule("fruits-apple");
        var fruitsOrange10 = fruits10.addModule("fruits-orange");

        var fruits20 = platformGenerator.configureProject("org.fruits", "fruits-parent", "2.0")
                .setScm("https://fruits.org", "2.0");
        var fruitsApple20 = fruits20.addModule("fruits-apple");
        var fruitsOrange20 = fruits20.addModule("fruits-orange");

        var quarkusBom = platformGenerator.getQuarkusBom()
                .importBom(jacksonBom10)
                .addVersionConstraint(petsCat10);

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom");
        var camelBom = camelProject.addPomModule("camel-bom")
                .importBom(jacksonBom20)
                .addVersionConstraint(camelAtom)
                .addVersionConstraint(petsDog20)
                .addVersionConstraint(fruitsApple10);

        var amqProject20 = platformGenerator.configureProject("org.amq", "amq-parent", "2.0")
                .setScm("https://amq.org", "2.0");
        var amqJms20 = amqProject20.addQuarkusExtensionRuntimeModule("amq-jms");
        var amqBom20 = amqProject20.addPomModule("amq-bom")
                .addVersionConstraint(amqJms20)
                .addVersionConstraint(fruitsOrange20);

        var amqProject21 = platformGenerator.configureProject("org.amq", "amq-parent", "2.1")
                .setScm("https://amq.org", "2.1");
        var amqJms21 = amqProject21.addQuarkusExtensionRuntimeModule("amq-jms");
        var amqBom21 = amqProject21.addPomModule("amq-bom")
                .addVersionConstraint(amqJms21)
                .addVersionConstraint(fruitsOrange20);

        var platformBuilder = platformGenerator
                .setProjectDir(workingDir)
                .addMember("Camel", camelBom)
                .addMember("AMQ", amqBom20)
                .generateProject();

        var platform = platformBuilder.build();

        Set<ArtifactCoords> members = Set.of(
                ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "quarkus-bom",
                        PlatformTestProjectGenerator.DEFAULT_VERSION),
                ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "quarkus-camel-bom", "1.0"),
                ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "quarkus-amq-bom", "2.0"));

        assertThat(platform.getCore()).isNotNull();
        assertThat(platform.getCore().getExtensionCatalog()).isNotNull();
        assertThat(platform.getCore().getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(platform.getCore().getPlatformStream()).isEqualTo("1");
        assertThat(platform.getCore().getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(platform.getCore().getReleaseMembers()).isEqualTo(members);
        assertThat(platform.getCore().getBom()).isNotNull();
        assertThat(platform.getCore().getBom().getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(platform.getCore().containsConstraint(
                ArtifactCoords.jar("io.quarkus", "quarkus-core", platform.getCore().getQuarkusCoreVersion()))).isTrue();
        assertThat(platform.getCore().containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(petsCat10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(petsDog10.getArtifactCoords())).isFalse();

        assertThat(platform.getUniverse()).isNotNull();
        assertThat(platform.getUniverse().getExtensionCatalog()).isNotNull();
        assertThat(platform.getUniverse().getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(platform.getUniverse().getPlatformStream()).isEqualTo("1");
        assertThat(platform.getUniverse().getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(platform.getUniverse().getReleaseMembers())
                .isEqualTo(Set.of(ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "acme-quarkus-universe-bom",
                        PlatformTestProjectGenerator.DEFAULT_VERSION)));
        assertThat(platform.getUniverse().getBom()).isNotNull();
        assertThat(platform.getUniverse().getBom().getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(platform.getUniverse().containsConstraint(
                ArtifactCoords.jar("io.quarkus", "quarkus-core", platform.getCore().getQuarkusCoreVersion()))).isTrue();
        assertThat(platform.getUniverse().containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(petsCat10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(petsDog10.getArtifactCoords())).isTrue();
        assertThat(platform.getUniverse().containsConstraint(petsDog20.getArtifactCoords())).isFalse();

        assertThat(platform.getMembers()).hasSize(2);

        var camel = platform.getMember("Camel");
        assertThat(camel).isNotNull();
        assertThat(camel.getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(camel.getPlatformStream()).isEqualTo("1");
        assertThat(camel.getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(camel.getReleaseMembers()).isEqualTo(members);
        assertThat(camel.getBom().getVersion()).isEqualTo("1.0");
        assertThat(camel.containsConstraint(camelAtom.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(petsCat10.getArtifactCoords())).isFalse();
        assertThat(camel.containsConstraint(petsDog10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(petsDog20.getArtifactCoords())).isFalse();
        assertThat(camel.containsConstraint(fruitsApple20.getArtifactCoords())).isTrue();

        var amq = platform.getMember("AMQ");
        assertThat(amq).isNotNull();
        assertThat(amq.getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(amq.getPlatformStream()).isEqualTo("1");
        assertThat(amq.getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(amq.getReleaseMembers()).isEqualTo(members);
        assertThat(amq.getBom().getVersion()).isEqualTo("2.0");
        assertThat(amq.containsConstraint(amqJms20.getArtifactCoords())).isTrue();
        assertThat(amq.containsConstraint(fruitsOrange20.getArtifactCoords())).isTrue();

        // BUMP AMQ to 2.1
        platform = platformBuilder.build("-pl quarkus-platform-config clean install -Damq-bom.version=2.1");

        members = Set.of(
                ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "quarkus-bom",
                        PlatformTestProjectGenerator.DEFAULT_VERSION),
                ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "quarkus-camel-bom", "1.0"),
                ArtifactCoords.pom(PlatformTestProjectGenerator.DEFAULT_GROUP_ID, "quarkus-amq-bom", "2.1"));

        assertThat(platform.getCore()).isNotNull();
        assertThat(platform.getCore().getExtensionCatalog()).isNotNull();
        assertThat(platform.getCore().getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(platform.getCore().getPlatformStream()).isEqualTo("1");
        assertThat(platform.getCore().getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(platform.getCore().getReleaseMembers()).isEqualTo(members);
        assertThat(platform.getCore().getBom()).isNotNull();
        assertThat(platform.getCore().getBom().getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(platform.getCore().containsConstraint(
                ArtifactCoords.jar("io.quarkus", "quarkus-core", platform.getCore().getQuarkusCoreVersion()))).isTrue();
        assertThat(platform.getCore().containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(petsCat10.getArtifactCoords())).isTrue();
        assertThat(platform.getCore().containsConstraint(petsDog10.getArtifactCoords())).isFalse();

        assertThat(platform.getMembers()).hasSize(2);

        camel = platform.getMember("Camel");
        assertThat(camel).isNotNull();
        assertThat(camel.getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(camel.getPlatformStream()).isEqualTo("1");
        assertThat(camel.getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(camel.getReleaseMembers()).isEqualTo(members);
        assertThat(camel.getBom().getVersion()).isEqualTo("1.0");
        assertThat(camel.containsConstraint(camelAtom.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(jacksonLibA10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(jacksonLibB10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(petsCat10.getArtifactCoords())).isFalse();
        assertThat(camel.containsConstraint(petsDog10.getArtifactCoords())).isTrue();
        assertThat(camel.containsConstraint(petsDog20.getArtifactCoords())).isFalse();
        assertThat(camel.containsConstraint(fruitsApple20.getArtifactCoords())).isTrue();

        amq = platform.getMember("AMQ");
        assertThat(amq).isNotNull();
        assertThat(amq.getPlatformKey()).isEqualTo("org.acme.quarkus.platform");
        assertThat(amq.getPlatformStream()).isEqualTo("1");
        assertThat(amq.getPlatformVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(amq.getReleaseMembers()).isEqualTo(members);
        assertThat(amq.getBom().getVersion()).isEqualTo("2.1");
        assertThat(amq.containsConstraint(amqJms21.getArtifactCoords())).isTrue();
        assertThat(amq.containsConstraint(fruitsOrange20.getArtifactCoords())).isTrue();
    }
}
