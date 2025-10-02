package io.quarkus.domino.recipes.location;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeLayoutManagerTest {
    @Test
    public void testPaths(@TempDir Path tempDir) {
        RecipeLayoutManager recipeLayoutManager = new RecipeLayoutManager(tempDir);

        recipeLayoutManager.getAllRepositoryPaths();
    }
}
