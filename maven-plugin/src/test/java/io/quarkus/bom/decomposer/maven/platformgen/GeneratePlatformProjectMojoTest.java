package io.quarkus.bom.decomposer.maven.platformgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratePlatformProjectMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void doesNotReuseAnUnrelatedPropertyWithTheSameVersion() throws Exception {
        var mojo = newMojo(Map.of("quarkus-flow.version", "1.1.3"),
                List.of("<artifact>io.quarkiverse.flow:quarkus-flow:${quarkus-flow.version}</artifact>"));

        assertEquals("1.1.3", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
        assertEquals("${quarkus-flow.version}", testArtifactVersion(mojo, "io.quarkiverse.flow", "1.1.3"));
    }

    @Test
    void preservesExistingBehaviorWhenNoPropertyOwnerCanBeInferred() throws Exception {
        var mojo = newMojo(Map.of("shared.version", "1.1.3"), List.of("<project/>"));

        assertEquals("${shared.version}", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
    }

    @Test
    void preservesGroupSpecificMatchingForPropertiesWithTheSameValue() throws Exception {
        var mojo = newMojo(Map.of("quarkus-flow.version", "1.1.3", "certs.version", "1.1.3"),
                List.of("<artifact>io.quarkiverse.flow:quarkus-flow:${quarkus-flow.version}</artifact>",
                        "<artifact>io.smallrye.certs:smallrye-certificate-generator-junit5:${certs.version}</artifact>"));

        assertEquals("${quarkus-flow.version}", testArtifactVersion(mojo, "io.quarkiverse.flow", "1.1.3"));
        assertEquals("${certs.version}", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
    }

    @Test
    void keepsAnUnmatchedVersionLiteral() throws Exception {
        var mojo = newMojo(Map.of("quarkus-flow.version", "1.1.3"),
                List.of("<artifact>io.quarkiverse.flow:quarkus-flow:${quarkus-flow.version}</artifact>"));

        assertEquals("5.0.0", testArtifactVersion(mojo, "io.smallrye.certs", "5.0.0"));
    }

    private GeneratePlatformProjectMojo newMojo(Map<String, String> properties, List<String> lines)
            throws IOException {
        Model model = new Model();
        properties.forEach(model::addProperty);
        MavenProject project = new MavenProject(model);
        project.setOriginalModel(model);
        Path pomFile = tempDir.resolve("pom.xml");
        Files.write(pomFile, lines, StandardCharsets.UTF_8);
        project.setFile(pomFile.toFile());

        var mojo = new GeneratePlatformProjectMojo();
        mojo.project = project;
        return mojo;
    }

    private static String testArtifactVersion(GeneratePlatformProjectMojo mojo, String groupId, String version)
            throws Exception {
        Method method = GeneratePlatformProjectMojo.class
                .getDeclaredMethod("getTestArtifactVersion", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(mojo, groupId, version);
    }
}
