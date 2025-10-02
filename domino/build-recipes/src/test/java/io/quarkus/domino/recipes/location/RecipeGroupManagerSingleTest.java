package io.quarkus.domino.recipes.location;

import io.quarkus.domino.recipes.BuildRecipe;
import io.quarkus.domino.recipes.GAV;
import io.quarkus.domino.recipes.build.BuildRecipeInfo;
import io.quarkus.domino.recipes.scm.ScmInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RecipeGroupManagerSingleTest {
    static RecipeGroupManager manager;

    @BeforeAll
    public static void setup() throws Exception {
        //noinspection DataFlowIssue
        Path path = Paths.get(RecipeGroupManagerSingleTest.class.getClassLoader().getResource("test-recipes").toURI());
        manager = new RecipeGroupManager(List.of(new RecipeLayoutManager(path)));
    }

    @Test
    public void testGroupIdBasedRecipe() {
        GAV req = new GAV("io.quarkus", "quarkus-core", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/quarkus.git",
                readScmUrl(result.get(0)));

        req = new GAV("io.quarkus.security", "quarkus-security", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/quarkus-security.git",
                readScmUrl(result.get(0)));
    }

    @Test
    public void testVersionOverride() {
        GAV req = new GAV("io.quarkus", "quarkus-core", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.get(0)));
        req = new GAV("io.quarkus", "quarkus-core", "0.9");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.get(0)));
    }

    @Test
    public void testArtifactOverride() {
        GAV req = new GAV("io.quarkus", "quarkus-gizmo", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/gizmo.git",
                readScmUrl(result.get(0)));
    }

    @Test
    public void testArtifactAndVersionOverride() {
        GAV req = new GAV("io.quarkus", "quarkus-gizmo", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.get(0)));
        req = new GAV("io.quarkus", "quarkus-gizmo", "0.9");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.get(0)));
    }

    @Test
    public void testNoGroupLevelBuild() {
        GAV req = new GAV("io.vertx", "not-real", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testBuildInfoRecipe() throws IOException {
        var result = manager.requestBuildInformation(
                new BuildInfoRequest("https://github.com/quarkusio/quarkus.git", "2.0", Set.of(BuildRecipe.BUILD)));
        Assertions.assertEquals(List.of("-DskipDocs=true"),
                BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD)).getAdditionalArgs());
        Assertions.assertEquals(List.of(".:.*-javadoc\\.jar:.*"),
                BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD)).getAllowedDifferences());
        result = manager.requestBuildInformation(
                new BuildInfoRequest("https://github.com/quarkusio/quarkus.git", "1.0", Set.of(BuildRecipe.BUILD)));
        Assertions.assertEquals(List.of("-Dquarkus1=true"),
                BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD)).getAdditionalArgs());
        Assertions.assertEquals(List.of(".:.*-sources\\.jar:.*"),
                BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD)).getAllowedDifferences());
        result = manager.requestBuildInformation(
                new BuildInfoRequest("https://github.com/quarkusio/quarkus.git", "0.1", Set.of(BuildRecipe.BUILD)));
        Assertions.assertEquals(List.of(),
                BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD)).getAdditionalArgs());
        Assertions.assertEquals(List.of(),
                BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD)).getAllowedDifferences());
    }

    @Test
    public void testBuildInfoRecipeAdditional() throws IOException {
        BuildInfoResponse result = manager.requestBuildInformation(
                new BuildInfoRequest("https://github.com/lz4/lz4-java.git", "1.0", Set.of(BuildRecipe.BUILD)));

        BuildRecipeInfo recipeInfo = BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD));
        Map<String, BuildRecipeInfo> recipeInfoMap = recipeInfo.getAdditionalBuilds();

        Assertions.assertEquals(List.of("-Dlz4-pure-java=true"),
                recipeInfoMap.get("pureJava").getAdditionalArgs());
        Assertions.assertTrue(recipeInfoMap.get("pureJava").isEnforceVersion());
        Assertions.assertTrue(recipeInfoMap.get("pureJava").getPreBuildScript().startsWith("sed -i"));
    }

    @Test
    public void testArtifactLZ4()
            throws IOException {
        GAV req = new GAV("org.lz4", "lz4", "1.8.0");
        List<Path> scmLookup = manager.lookupScmInformation(req);
        var result = BuildRecipe.SCM.getHandler().parse(scmLookup.get(0));

        Assertions.assertNull(result.getBuildNameFragment());
        Assertions.assertEquals("https://github.com/lz4/lz4-java.git", result.getUri());

        req = new GAV("org.lz4", "lz4-pure-java", "1.8.0");
        scmLookup = manager.lookupScmInformation(req);
        result = BuildRecipe.SCM.getHandler().parse(scmLookup.get(0));

        Assertions.assertEquals("pureJava", result.getBuildNameFragment());
        Assertions.assertEquals("https://github.com/lz4/lz4-java.git", result.getUriWithoutFragment());
    }

    private String readScmUrl(Path scmPath) {
        if (scmPath == null) {
            return "";
        }
        try {
            ScmInfo parse = BuildRecipe.SCM.getHandler().parse(scmPath);
            if (parse == null) {
                return "";
            }
            return parse.getUri();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
