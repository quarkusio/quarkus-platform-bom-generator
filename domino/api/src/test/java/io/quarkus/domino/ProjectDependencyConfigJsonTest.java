package io.quarkus.domino;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.json;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProjectDependencyConfigJsonTest {

    @TempDir
    Path testDir;

    @Test
    public void emptyConfig() throws Exception {
        var configFile = getTestConfig();
        ProjectDependencyConfig.builder().build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{}"));
    }

    @Test
    public void nonDefaultLevel() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setLevel(0)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{level: 0}"));
    }

    @Test
    public void nonDefaultExcludeScope() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setExcludeScopes(Set.of("test"))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-scopes: [\"test\"]}"));
    }

    @Test
    public void projectDir() throws Exception {
        final Path configFile = getTestConfig();
        final Path projectDir = Path.of("").normalize().toAbsolutePath();
        ProjectDependencyConfig.builder()
                .setProjectDir(projectDir)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{project-dir: \"" + projectDir.toUri() + "\"}"));
    }

    @Test
    public void projectBom() throws Exception {
        final Path configFile = getTestConfig();
        final ArtifactCoords bom = ArtifactCoords.pom("org.acme", "acme-bom", "1.0");
        ProjectDependencyConfig.builder()
                .setProjectBom(bom)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{project-bom: \"" + bom.toGACTVString() + "\"}"));
    }

    @Test
    public void projectArtifacts() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.jar("org.acme", "acme-core", "1.0"),
                ArtifactCoords.jar("io.other", "other-lib", "2.0"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setProjectArtifacts(list)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{project-artifacts: " + jsonList + "}"));
    }

    @Test
    public void includeArtifacts() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.jar("org.acme", "acme-core", "1.0"),
                ArtifactCoords.jar("io.other", "other-lib", "2.0"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setIncludeArtifacts(list)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-artifacts: " + jsonList + "}"));
    }

    @Test
    public void includeGroupIds() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("org.acme", "*", "*", "*", "*"),
                ArtifactCoords.of("io.other", "*", "*", "*", "*"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setIncludeGroupIds(List.of("org.acme", "io.other"))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-patterns: " + jsonList + "}"));
    }

    @Test
    public void includeKeys() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("org.acme", "acme-core", "", "jar", "*"),
                ArtifactCoords.of("io.other", "other-lib", "test-jar", "*", "*"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setIncludeKeys(List.of(
                        ArtifactKey.ga("org.acme", "acme-core"),
                        ArtifactKey.of("io.other", "other-lib", "test-jar", "*")))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-patterns: " + jsonList + "}"));
    }

    @Test
    public void includePatterns() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("*", "acme-core", "", "*", "*"),
                ArtifactCoords.of("io.other", "*", "test-jar", "*", "*"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setIncludePatterns(list)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-patterns: " + jsonList + "}"));
    }

    @Test
    public void excludeGroupIds() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("org.acme", "*", "*", "*", "*"),
                ArtifactCoords.of("io.other", "*", "*", "*", "*"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setExcludeGroupIds(List.of("org.acme", "io.other"))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-patterns: " + jsonList + "}"));
    }

    @Test
    public void excludeKeys() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("org.acme", "acme-core", "", "jar", "*"),
                ArtifactCoords.of("io.other", "other-lib", "test-jar", "*", "*"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setExcludeKeys(List.of(
                        ArtifactKey.ga("org.acme", "acme-core"),
                        ArtifactKey.of("io.other", "other-lib", "test-jar", "*")))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-patterns: " + jsonList + "}"));
    }

    @Test
    public void excludePatterns() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("*", "acme-core", "", "*", "*"),
                ArtifactCoords.of("io.other", "*", "test-jar", "*", "*"));
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setExcludePatterns(list)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-patterns: " + jsonList + "}"));
    }

    @Test
    public void includeNonManaged() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setIncludeNonManaged(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-non-managed: true}"));
    }

    @Test
    public void excludeParentPoms() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setExcludeParentPoms(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-parent-poms: true}"));
    }

    @Test
    public void excludeBomImports() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setExcludeBomImports(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-bom-imports: true}"));
    }

    @Test
    public void recipeRepos() throws Exception {
        final Path configFile = getTestConfig();
        final List<String> list = List.of(
                "https://acme.recipes.org/recipes",
                "https://other.io/recipes");
        final StringBuilder jsonList = new StringBuilder().append("[");
        var i = list.iterator();
        while (i.hasNext()) {
            var coords = i.next();
            jsonList.append("\"").append(coords).append("\"");
            if (i.hasNext()) {
                jsonList.append(",");
            }
        }
        jsonList.append("]");
        ProjectDependencyConfig.builder()
                .setRecipeRepos(list)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{recipe-repos: " + jsonList + "}"));
    }

    @Test
    public void warnOnResolutionErrors() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setWarnOnResolutionErrors(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{warn-on-resolution-errors: true}"));
    }

    @Test
    public void warnOnMissingScm() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setWarnOnMissingScm(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{warn-on-missing-scm: true}"));
    }

    @Test
    public void includeAlreadyBuilt() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setIncludeAlreadyBuilt(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-already-built: true}"));
    }

    @Test
    public void includeOptionalDeps() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setIncludeOptionalDeps(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-optional-deps: true}"));
    }

    @Test
    public void gradleJava8() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setGradleJava8(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{gradle-java8: true}"));
    }

    @Test
    public void gradleJavaHome() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setGradleJavaHome("/home/java/version")
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{gradle-java-home: \"/home/java/version\"}"));
    }

    private Path getTestConfig() {
        return testDir.resolve("test-config.json");
    }
}
