package io.quarkus.domino.recipes.location;

import io.quarkus.domino.recipes.BuildRecipe;
import java.util.Set;

public class BuildInfoRequest {

    private final String scmUri;
    private final String version;
    private final Set<BuildRecipe> recipeFiles;

    public BuildInfoRequest(String scmUri, String version, Set<BuildRecipe> recipeFiles) {
        this.scmUri = scmUri;
        this.version = version;
        this.recipeFiles = recipeFiles;
    }

    public Set<BuildRecipe> getRecipeFiles() {
        return recipeFiles;
    }

    public String getScmUri() {
        return scmUri;
    }

    public String getVersion() {
        return version;
    }
}
