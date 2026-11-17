package io.quarkus.bom.decomposer.maven.platformgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.bom.platform.PlatformMemberConfig;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                List.of("<bom>io.quarkiverse.flow:quarkus-flow-bom:${quarkus-flow.version}</bom>"),
                member("io.quarkiverse.flow:quarkus-flow-bom:1.1.3"));

        assertEquals("1.1.3", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
        assertEquals("${quarkus-flow.version}", testArtifactVersion(mojo, "io.quarkiverse.flow", "1.1.3"));
    }

    /**
     * Only the artifacts configured for the platform are looked for in the POM, so a property that is
     * not the version of any of them may still be the version of the artifact being resolved. Those
     * include the dependencies of the test artifacts and of the attached Maven plugin.
     */
    @Test
    void preservesExistingBehaviorWhenNoPropertyOwnerCanBeInferred() throws Exception {
        var mojo = newMojo(Map.of("shared.version", "1.1.3"), List.of("<project/>"));

        assertEquals("${shared.version}", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
    }

    @Test
    void preservesGroupSpecificMatchingForPropertiesWithTheSameValue() throws Exception {
        var mojo = newMojo(Map.of("quarkus-flow.version", "1.1.3", "certs.version", "1.1.3"),
                List.of("<bom>io.quarkiverse.flow:quarkus-flow-bom:${quarkus-flow.version}</bom>",
                        "<artifact>io.smallrye.certs:smallrye-certificate-generator-junit5:${certs.version}</artifact>"),
                member("io.quarkiverse.flow:quarkus-flow-bom:1.1.3",
                        "io.smallrye.certs:smallrye-certificate-generator-junit5:1.1.3"));

        assertEquals("${quarkus-flow.version}", testArtifactVersion(mojo, "io.quarkiverse.flow", "1.1.3"));
        assertEquals("${certs.version}", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
    }

    @Test
    void keepsAnUnmatchedVersionLiteral() throws Exception {
        var mojo = newMojo(Map.of("quarkus-flow.version", "1.1.3"),
                List.of("<bom>io.quarkiverse.flow:quarkus-flow-bom:${quarkus-flow.version}</bom>"),
                member("io.quarkiverse.flow:quarkus-flow-bom:1.1.3"));

        assertEquals("5.0.0", testArtifactVersion(mojo, "io.smallrye.certs", "5.0.0"));
    }

    /**
     * A groupId expressed as a property expression must be interpolated before the coordinates can be
     * recognized as the ones of a configured artifact.
     */
    @Test
    void interpolatesGroupIdExpressionsWhenLookingUpThePropertyOwner() throws Exception {
        var mojo = newMojo(
                Map.of("quarkus.platform.group-id", "io.quarkus.platform", "quarkus.version", "1.1.3"),
                List.of("<bom>${quarkus.platform.group-id}:quarkus-bom:${quarkus.version}</bom>"),
                member("io.quarkus.platform:quarkus-bom:1.1.3"));

        assertEquals("${quarkus.version}", testArtifactVersion(mojo, "io.quarkus.platform", "1.1.3"));
    }

    /**
     * A single version property commonly covers several groupIds of the same project, e.g. the Quarkus
     * core release versions io.quarkus, io.quarkus.arc and io.quarkus.security alike, while the platform
     * configuration only ever spells out io.quarkus.
     */
    @Test
    void usesTheVersionPropertyForSiblingGroupIdsOfTheSameProject() throws Exception {
        var mojo = newMojo(Map.of("quarkus.version", "1.1.3"),
                List.of("<bom>io.quarkus:quarkus-bom:${quarkus.version}</bom>"),
                member("io.quarkus:quarkus-bom:1.1.3"));

        assertEquals("${quarkus.version}", testArtifactVersion(mojo, "io.quarkus", "1.1.3"));
        assertEquals("${quarkus.version}", testArtifactVersion(mojo, "io.quarkus.arc", "1.1.3"));
        assertEquals("${quarkus.version}", testArtifactVersion(mojo, "io.quarkus.security", "1.1.3"));
        // not a sub-group of io.quarkus, despite the shared prefix of the string
        assertEquals("1.1.3", testArtifactVersion(mojo, "io.quarkusiverse", "1.1.3"));
    }

    /**
     * An artifact whose groupId is the one of a configured artifact must win over a sibling match
     * from a different property with the same value.
     */
    @Test
    void prefersTheExactGroupIdOwnerOverASiblingMatch() throws Exception {
        var mojo = newMojo(Map.of("quarkus.version", "1.1.3", "quarkus.arc.version", "1.1.3"),
                List.of("<bom>io.quarkus:quarkus-bom:${quarkus.version}</bom>",
                        "<artifact>io.quarkus.arc:arc:${quarkus.arc.version}</artifact>"),
                member("io.quarkus:quarkus-bom:1.1.3", "io.quarkus.arc:arc:1.1.3"));

        assertEquals("${quarkus.version}", testArtifactVersion(mojo, "io.quarkus", "1.1.3"));
        assertEquals("${quarkus.arc.version}", testArtifactVersion(mojo, "io.quarkus.arc", "1.1.3"));
    }

    /**
     * When several properties share a value and none of them is the version of a configured artifact,
     * there is nothing to choose between them, so the version is kept literal instead of picking one
     * in the order the project properties happen to be iterated in.
     */
    @Test
    void keepsTheLiteralVersionWhenPropertiesShareAValueAndNoneCanBeAttributed() throws Exception {
        var mojo = newMojo(Map.of("assertj.version", "1.1.3", "some-other.version", "1.1.3"),
                List.of("<project/>"));

        assertEquals("1.1.3", testArtifactVersion(mojo, "org.assertj", "1.1.3"));
    }

    /**
     * A line that is not the coordinates of a configured artifact must not be mistaken for one,
     * however much it looks like artifact coordinates.
     */
    @Test
    void ignoresPomLinesThatAreNotConfiguredArtifactCoords() throws Exception {
        var mojo = newMojo(Map.of("shared.version", "1.1.3"),
                List.of("<url>http://localhost:8080/${shared.version}/repo</url>",
                        "<arg>-Dfoo=bar:baz:${shared.version}</arg>"));

        // the property is not attributed to any groupId, so it remains usable
        assertEquals("${shared.version}", testArtifactVersion(mojo, "io.smallrye.certs", "1.1.3"));
    }

    /**
     * A member BOM is often expressed with a property that is an alias of the one actually holding the
     * version, e.g. quarkus-bom.version set to ${quarkus.version}. The property holding the version is
     * the meaningful one, the alias merely happens to be the one next to the coordinates in the POM.
     */
    @Test
    void prefersThePropertyHoldingTheVersionOverAnAliasOfIt() throws Exception {
        var mojo = newMojo(
                // as declared in the POM
                Map.of("quarkus.version", "1.1.3", "quarkus-bom.version", "${quarkus.version}"),
                // as Maven resolves them
                Map.of("quarkus.version", "1.1.3", "quarkus-bom.version", "1.1.3"),
                List.of("<bom>io.quarkus:quarkus-bom:${quarkus-bom.version}</bom>"),
                member("io.quarkus:quarkus-bom:1.1.3"));

        assertEquals("${quarkus.version}", testArtifactVersion(mojo, "io.quarkus", "1.1.3"));
    }

    private static PlatformMemberConfig member(String... coords) {
        var member = new PlatformMemberConfig();
        member.setBom(coords[0]);
        for (int i = 1; i < coords.length; ++i) {
            var test = new io.quarkus.bom.platform.PlatformMemberTestConfig();
            test.setArtifact(coords[i]);
            member.addTest(test);
        }
        return member;
    }

    private GeneratePlatformProjectMojo newMojo(Map<String, String> properties, List<String> lines,
            PlatformMemberConfig... members) throws IOException {
        return newMojo(properties, properties, lines, members);
    }

    /**
     * @param originalProperties properties as they are declared in the POM
     * @param effectiveProperties properties with their expressions resolved, as Maven provides them
     */
    private GeneratePlatformProjectMojo newMojo(Map<String, String> originalProperties,
            Map<String, String> effectiveProperties, List<String> lines, PlatformMemberConfig... members)
            throws IOException {
        Model originalModel = new Model();
        originalProperties.forEach(originalModel::addProperty);
        Model model = new Model();
        effectiveProperties.forEach(model::addProperty);
        MavenProject project = new MavenProject(model);
        project.setOriginalModel(originalModel);
        Path pomFile = tempDir.resolve("pom.xml");
        Files.write(pomFile, lines, StandardCharsets.UTF_8);
        project.setFile(pomFile.toFile());

        var mojo = new GeneratePlatformProjectMojo();
        mojo.project = project;
        mojo.platformConfig = new PlatformConfig();
        mojo.platformConfig.setMembers(new ArrayList<>(List.of(members)));
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
