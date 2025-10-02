package io.quarkus.domino.recipes.build;

import io.quarkus.domino.recipes.RecipeManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.logging.Logger;

public class BuildRecipeInfoManager implements RecipeManager<BuildRecipeInfo> {

    private static final Logger log = Logger.getLogger(BuildRecipeInfoManager.class.getName());

    @Override
    public BuildRecipeInfo parse(InputStream file)
            throws IOException {
        log.info("Parsing " + file + " for build recipe information");
        BuildRecipeInfo buildRecipeInfo = null;
        if (file.available() != 0) {
            buildRecipeInfo = MAPPER.readValue(file, BuildRecipeInfo.class);
        }
        return Objects.requireNonNullElseGet(buildRecipeInfo, BuildRecipeInfo::new);
    }

    @Override
    public void write(BuildRecipeInfo data, OutputStream out)
            throws IOException {
        MAPPER.writeValue(out, data);
    }
}
