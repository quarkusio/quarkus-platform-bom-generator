package com.redhat.hacbs.recipes.scm;

import com.redhat.hacbs.recipes.RecipeManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ScmInfoManager implements RecipeManager<ScmInfo> {

    @Override
    public ScmInfo parse(InputStream file) throws IOException {
        ScmInfo info = MAPPER.readValue(file, ScmInfo.class);
        if (info == null) {
            return new ScmInfo(); //can happen on empty file
        }
        return info;
    }

    @Override
    public void write(ScmInfo data, OutputStream out) throws IOException {
        MAPPER.writeValue(out, data);
    }
}
