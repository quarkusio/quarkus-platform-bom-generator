package com.redhat.hacbs.recipes.tools;

import com.redhat.hacbs.recipes.RecipeManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class BuildToolInfoManager implements RecipeManager<List<BuildToolInfo>> {

    public static BuildToolInfoManager INSTANCE = new BuildToolInfoManager();

    @Override
    public List<BuildToolInfo> parse(InputStream file) throws IOException {
        return MAPPER.readerForListOf(BuildToolInfo.class).readValue(file);
    }

    @Override
    public void write(List<BuildToolInfo> data, OutputStream out) throws IOException {
        MAPPER.writeValue(out, data);
    }
}
