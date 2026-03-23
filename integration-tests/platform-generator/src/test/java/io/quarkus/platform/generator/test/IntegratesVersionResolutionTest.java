package io.quarkus.platform.generator.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.maven.project.ExtensionIntegrates;
import io.quarkus.platform.generator.PlatformTestProjectGenerator;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntegratesVersionResolutionTest {

    @TempDir
    Path workingDir;

    @Test
    void testIntegratesVersionResolvedFromExplicitBomConstraint() throws Exception {
        var platformGenerator = PlatformTestProjectGenerator.newInstance();

        var jackson10 = platformGenerator.configureProject("org.jackson", "jackson-parent", "1.0")
                .setScm("https://jackson.org", "1.0");
        var jacksonLibA10 = jackson10.addModule("jackson-lib-a");

        var jackson20 = platformGenerator.configureProject("org.jackson", "jackson-parent", "2.0")
                .setScm("https://jackson.org", "2.0");
        var jacksonLibA20 = jackson20.addModule("jackson-lib-a");

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom")
                .addExtensionIntegratesMetadata(List.of(
                        new ExtensionIntegrates("Jackson Lib A", "org.jackson:jackson-lib-a", "1.0")));

        var camelBom = camelProject.addPomModule("camel-bom")
                .addVersionConstraint(camelAtom) // version constraint
                .addVersionConstraint(jacksonLibA20);

        var platform = platformGenerator
                .setProjectDir(workingDir)
                .addMember("Camel", camelBom)
                .generateProject()
                .build();

        assertThat(platform.getCore()).isNotNull();
        assertThat(platform.getCore().getExtensionCatalog()).isNotNull();

        var camelExtensions = platform.getMember("Camel").getExtensionCatalog().getExtensions();
        var camelAtomExt = camelExtensions.stream()
                .filter(ext -> ext.getArtifact().getArtifactId().equals("camel-atom"))
                .findFirst();

        assertThat(camelAtomExt).as("camel-atom extension should be present").isPresent();

        Map<String, Object> camelMetadata = camelAtomExt.get().getMetadata();
        assertThat(camelMetadata).containsKey("integrates");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> camelIntegrates = (List<Map<String, String>>) camelMetadata.get("integrates");
        assertThat(camelIntegrates).hasSize(1);

        Map<String, String> jacksonEntry = camelIntegrates.get(0);
        assertThat(jacksonEntry.get("name")).isEqualTo("Jackson Lib A");
        assertThat(jacksonEntry.get("artifact")).isEqualTo("org.jackson:jackson-lib-a");
        assertThat(jacksonEntry.get("version"))
                .as("Jackson version in camel-atom should be upgraded from 1.0 to 2.0 to match camel BOM")
                .isEqualTo("2.0");
    }

    @Test
    void testIntegratesVersionResolvedFromImportedBom() throws Exception {
        var platformGenerator = PlatformTestProjectGenerator.newInstance();

        var jackson10 = platformGenerator.configureProject("org.jackson", "jackson-parent", "1.0")
                .setScm("https://jackson.org", "1.0");
        var jacksonLibA10 = jackson10.addModule("jackson-lib-a");
        var jacksonBom10 = jackson10.addPomModule("jackson-bom")
                .addVersionConstraint(jacksonLibA10);

        var jackson20 = platformGenerator.configureProject("org.jackson", "jackson-parent", "2.0")
                .setScm("https://jackson.org", "2.0");
        var jacksonLibA20 = jackson20.addModule("jackson-lib-a");
        var jacksonBom20 = jackson20.addPomModule("jackson-bom")
                .addVersionConstraint(jacksonLibA20);

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom")
                .addExtensionIntegratesMetadata(List.of(
                        new ExtensionIntegrates("Jackson Lib A", "org.jackson:jackson-lib-a", "1.0")));

        var camelBom = camelProject.addPomModule("camel-bom")
                .importBom(jacksonBom20)
                .addVersionConstraint(camelAtom);

        var platform = platformGenerator
                .setProjectDir(workingDir)
                .addMember("Camel", camelBom)
                .generateProject()
                .build();

        assertThat(platform.getCore()).isNotNull();
        assertThat(platform.getCore().getExtensionCatalog()).isNotNull();

        var camelExtensions = platform.getMember("Camel").getExtensionCatalog().getExtensions();
        var camelAtomExt = camelExtensions.stream()
                .filter(ext -> ext.getArtifact().getArtifactId().equals("camel-atom"))
                .findFirst();

        assertThat(camelAtomExt).as("camel-atom extension should be present").isPresent();

        Map<String, Object> camelMetadata = camelAtomExt.get().getMetadata();
        assertThat(camelMetadata).containsKey("integrates");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> camelIntegrates = (List<Map<String, String>>) camelMetadata.get("integrates");
        assertThat(camelIntegrates).hasSize(1);

        Map<String, String> jacksonEntry = camelIntegrates.get(0);
        assertThat(jacksonEntry.get("name")).isEqualTo("Jackson Lib A");
        assertThat(jacksonEntry.get("artifact")).isEqualTo("org.jackson:jackson-lib-a");
        assertThat(jacksonEntry.get("version"))
                .as("Version should be upgraded from 1.0 to 2.0 via BOM import - " +
                        "Maven's getManagedDependencies() expands <scope>import</scope> BOMs")
                .isEqualTo("2.0");
    }
}
