package io.quarkus.domino.recipes.location;

import io.quarkus.domino.recipes.BuildRecipe;
import java.nio.file.Path;
import java.util.Map;

public class BuildInfoResponse {
    final Map<BuildRecipe, Path> data;

    public BuildInfoResponse(Map<BuildRecipe, Path> data) {
        this.data = data;
    }

    public Map<BuildRecipe, Path> getData() {
        return data;
    }
}
