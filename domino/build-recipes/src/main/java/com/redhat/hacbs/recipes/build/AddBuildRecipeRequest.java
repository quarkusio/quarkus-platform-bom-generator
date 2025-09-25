package com.redhat.hacbs.recipes.build;

import com.redhat.hacbs.recipes.BuildRecipe;

public class AddBuildRecipeRequest<T> {

    private final BuildRecipe<T> recipe;
    private final T data;
    private final String scmUri;
    private final String version;

    public AddBuildRecipeRequest(BuildRecipe<T> recipe, T data, String scmUri, String version) {
        this.recipe = recipe;
        this.data = data;
        this.scmUri = scmUri;
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public BuildRecipe<T> getRecipe() {
        return recipe;
    }

    public T getData() {
        return data;
    }

    public String getScmUri() {
        return scmUri;
    }
}
