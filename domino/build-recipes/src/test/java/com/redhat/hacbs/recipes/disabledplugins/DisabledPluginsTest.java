package com.redhat.hacbs.recipes.disabledplugins;

import static com.redhat.hacbs.recipes.disabledplugins.DisabledPluginsManager.DISABLED_PLUGINS_GRADLE;
import static com.redhat.hacbs.recipes.disabledplugins.DisabledPluginsManager.DISABLED_PLUGINS_MAVEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.redhat.hacbs.recipes.BuildRecipe;
import com.redhat.hacbs.recipes.location.BuildInfoRequest;
import com.redhat.hacbs.recipes.location.RecipeGroupManager;
import com.redhat.hacbs.recipes.location.RecipeLayoutManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DisabledPluginsTest {
    @Test
    void testReadYamlMaven() throws URISyntaxException, IOException {
        var disabledPlugins = new DisabledPlugins("org.glassfish.copyright:glassfish-copyright-maven-plugin",
                "org.sonatype.plugins:nexus-staging-maven-plugin", "com.mycila:license-maven-plugin",
                "org.codehaus.mojo:findbugs-maven-plugin", "de.jjohannes:gradle-module-metadata-maven-plugin");
        var url = DisabledPluginsTest.class.getClassLoader().getResource("disabled-plugins/" + DISABLED_PLUGINS_MAVEN);
        assertThat(url).isNotNull();
        var manager = new DisabledPluginsManager();
        var read = manager.parse(Path.of(url.toURI()));
        assertThat(read.getDisabledPlugins()).containsExactlyElementsOf(disabledPlugins.getDisabledPlugins());
    }

    @Test
    void testReadYamlGradle() throws URISyntaxException, IOException {
        var disabledPlugins = new DisabledPlugins("kotlin.gradle.targets.js", "org.jetbrains.dokka");
        var url = DisabledPluginsTest.class.getClassLoader().getResource("disabled-plugins/" + DISABLED_PLUGINS_GRADLE);
        assertThat(url).isNotNull();
        var manager = new DisabledPluginsManager();
        var read = manager.parse(Path.of(url.toURI()));
        assertThat(read.getDisabledPlugins()).containsExactlyElementsOf(disabledPlugins.getDisabledPlugins());
    }

    @Test
    void testRecipeInfo(@TempDir Path tempDir) throws URISyntaxException, IOException {
        var url = DisabledPluginsTest.class.getClassLoader().getResource("test-recipes");
        assertThat(url).isNotNull();
        var path = Paths.get(url.toURI());
        var manager = new RecipeGroupManager(List.of(new RecipeLayoutManager(path)));
        var result = manager.requestBuildInformation(
                new BuildInfoRequest("https://github.com/lz4/lz4-java.git", "1.0", Set.of(BuildRecipe.BUILD)));
        var recipeInfo = BuildRecipe.BUILD.getHandler().parse(result.getData().get(BuildRecipe.BUILD));
        assertThat(recipeInfo.getDisabledPlugins()).isNull();
        var result2 = manager.requestBuildInformation(
                new BuildInfoRequest("https://github.com/quarkusio/quarkus.git", "2.0", Set.of(BuildRecipe.BUILD)));
        var recipeInfo2 = BuildRecipe.BUILD.getHandler().parse(result2.getData().get(BuildRecipe.BUILD));
        assertThat(recipeInfo2.getDisabledPlugins()).containsExactly("foo:bar");
    }
}
