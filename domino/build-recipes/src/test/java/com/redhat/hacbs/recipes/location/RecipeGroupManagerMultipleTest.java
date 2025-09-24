package com.redhat.hacbs.recipes.location;

import com.redhat.hacbs.recipes.BuildRecipe;
import com.redhat.hacbs.recipes.GAV;
import com.redhat.hacbs.recipes.scm.ScmInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RecipeGroupManagerMultipleTest {
    static RecipeGroupManager manager;

    @BeforeAll
    public static void setup() throws Exception {
        Path opath = Paths.get(RecipeGroupManagerMultipleTest.class.getClassLoader().getResource("override").toURI());
        Path path = Paths.get(RecipeGroupManagerMultipleTest.class.getClassLoader().getResource("test-recipes").toURI());
        manager = new RecipeGroupManager(List.of(new RecipeLayoutManager(opath), new RecipeLayoutManager(path)));
    }

    @Test
    public void testGroupIdBasedRecipe() throws IOException {
        GAV req = new GAV("io.test", "test", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/test-override/test.git",
                readScmUrl(result.get(0)));
        Assertions.assertEquals("https://github.com/test/test.git",
                readScmUrl(result.get(1)));

        Assertions.assertTrue(
                BuildRecipe.SCM.getHandler().parse(result.get(0)).isPrivateRepo());

        req = new GAV("io.test.acme", "test-acme", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/test-override/test-acme.git",
                readScmUrl(result.get(0)));
        Assertions.assertFalse(
                BuildRecipe.SCM.getHandler().parse(result.get(0)).isPrivateRepo());

        req = new GAV("io.foo", "test-foo", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/foo/foo.git",
                readScmUrl(result.get(0)));
        Assertions.assertFalse(
                BuildRecipe.SCM.getHandler().parse(result.get(0)).isPrivateRepo());
    }

    @Test
    public void testVersionOverride() {
        //the original override should still work
        GAV req = new GAV("io.quarkus", "quarkus-core", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.get(0)));

        //but now we have added a new one as well
        req = new GAV("io.quarkus", "quarkus-core", "1.0-alpha2");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.get(0)));
    }

    @Test
    public void testArtifactOverride() {
        //this should still work as normal, it is not overriden
        GAV req = new GAV("io.quarkus", "quarkus-gizmo", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/gizmo.git",
                readScmUrl(result.get(0)));

        req = new GAV("io.test", "test-gizmo", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/test/gizmo.git",
                readScmUrl(result.get(0)));
    }

    @Test
    public void testArtifactAndVersionOverride() {
        //same here
        GAV req = new GAV("io.quarkus", "quarkus-gizmo", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.get(0)));

        req = new GAV("io.test", "test-gizmo", "1.0-alpha1");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.get(0)));

        req = new GAV("io.test", "test-gizmo", "0.9");
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
