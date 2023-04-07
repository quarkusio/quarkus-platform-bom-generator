package io.quarkus.domino;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.json;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    public void nonProjectBoms() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.jar("io.other", "other-lib", "2.0"),
                ArtifactCoords.jar("org.acme", "acme-core", "1.0"));
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
                .setNonProjectBoms(list)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{non-project-boms: " + jsonList + "}"));
    }

    @Test
    public void projectArtifacts() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.jar("io.other", "other-lib", "2.0"),
                ArtifactCoords.jar("org.acme", "acme-core", "1.0"));
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
                ArtifactCoords.jar("io.other", "other-lib", "2.0"),
                ArtifactCoords.jar("org.acme", "acme-core", "1.0"));
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
                ArtifactCoords.of("io.other", "*", "*", "*", "*"),
                ArtifactCoords.of("org.acme", "*", "*", "*", "*"));
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
                .setIncludeGroupIds(List.of("io.other", "org.acme"))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-patterns: " + jsonList + "}"));
    }

    @Test
    public void includeKeys() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("io.other", "other-lib", "test-jar", "*", "*"),
                ArtifactCoords.of("org.acme", "acme-core", "", "jar", "*"));
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
                ArtifactCoords.of("io.other", "*", "*", "*", "*"),
                ArtifactCoords.of("org.acme", "*", "*", "*", "*"));
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
                .setExcludeGroupIds(List.of("io.other", "org.acme"))
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{exclude-patterns: " + jsonList + "}"));
    }

    @Test
    public void excludeKeys() throws Exception {
        final Path configFile = getTestConfig();
        final List<ArtifactCoords> list = List.of(
                ArtifactCoords.of("io.other", "other-lib", "test-jar", "*", "*"),
                ArtifactCoords.of("org.acme", "acme-core", "", "jar", "*"));
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
                        ArtifactKey.of("io.other", "other-lib", "test-jar", "*"),
                        ArtifactKey.ga("org.acme", "acme-core")))
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
    public void includeNonManagedTrue() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setIncludeNonManaged(true)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{}"));
    }

    @Test
    public void includeNonManagedFalse() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setIncludeNonManaged(false)
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{include-non-managed: false}"));
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

    @Test
    public void productInfoId() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setId("product1")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {id: \"product1\"}}"));
    }

    @Test
    public void productInfoStream() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setStream("2.x")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {stream: \"2.x\"}}"));
    }

    @Test
    public void productInfoGroup() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setGroup("product.group")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {group: \"product.group\"}}"));
    }

    @Test
    public void productInfoName() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setName("product-name")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {name: \"product-name\"}}"));
    }

    @Test
    public void productInfoType() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setType("RUNTIME")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {type: \"RUNTIME\"}}"));
    }

    @Test
    public void productInfoVersion() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setVersion("V1")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {version: \"V1\"}}"));
    }

    @Test
    public void productInfoPurl() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setPurl("purl")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {purl: \"purl\"}}"));
    }

    @Test
    public void productInfoDescription() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setDescription("descr")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {description: \"descr\"}}"));
    }

    @Test
    public void productInfoCpe() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setCpe("CPE")
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {cpe: \"CPE\"}}"));
    }

    @Test
    public void productInfoReleaseNotesTitle() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setReleaseNotes(ProductReleaseNotes.builder()
                                .setTitle("Bugfree")
                                .build())
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile)).isEqualTo(json("{product-info: {release-notes: {title: \"Bugfree\"}}}"));
    }

    @Test
    public void productInfoReleaseNotesType() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setReleaseNotes(ProductReleaseNotes.builder()
                                .setType("service pack")
                                .build())
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile))
                .isEqualTo(json("{product-info: {release-notes: {type: \"service pack\"}}}"));
    }

    @Test
    public void productInfoReleaseNotesAliases() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setReleaseNotes(ProductReleaseNotes.builder()
                                .setAliases(List.of("one", "two"))
                                .build())
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile))
                .isEqualTo(json("{product-info: {release-notes: {aliases: [\"one\", \"two\"]}}}"));
    }

    @Test
    public void productInfoReleaseNotesProperties() throws Exception {
        final Path configFile = getTestConfig();
        ProjectDependencyConfig.builder()
                .setProductInfo(ProductInfo.builder()
                        .setReleaseNotes(ProductReleaseNotes.builder()
                                .setProperties(Map.of("one", "1", "two", "2"))
                                .build())
                        .build())
                .build().persist(configFile);
        assertThatJson(Files.readString(configFile))
                .isEqualTo(json("{product-info: {release-notes: {properties: {one: \"1\", two: \"2\"}}}}"));
    }

    private Path getTestConfig() {
        return testDir.resolve("test-config.json");
    }
}
