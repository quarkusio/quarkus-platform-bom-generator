package io.quarkus.domino.recipes.location;

import io.quarkus.domino.recipes.build.AddBuildRecipeRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface RecipeDirectory {

    /**
     * Returns the directories that contain the recipe information for this specific artifact
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     */
    Optional<RecipePathMatch> getArtifactPaths(String groupId, String artifactId, String version);

    Optional<Path> getBuildPaths(String scmUri, String version);

    Optional<Path> getRepositoryPaths(String name);

    Optional<Path> getBuildToolInfo(String name);

    List<Path> getAllRepositoryPaths();

    Optional<Path> getDisabledPlugins(String tool);

    default <T> void writeArtifactData(AddRecipeRequest<T> data) {
        throw new IllegalStateException("Not implemented");
    }

    default <T> void writeBuildData(AddBuildRecipeRequest<T> data) {
        throw new IllegalStateException("Not implemented");
    }

    /**
     * Updates to the latest version of the data
     */
    void update();
}
