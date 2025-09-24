package com.redhat.hacbs.recipes.disabledplugins;

import com.redhat.hacbs.recipes.RecipeManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

public class DisabledPluginsManager implements RecipeManager<DisabledPlugins> {
    public static final String DISABLED_PLUGINS_MAVEN = "maven.yaml";

    public static final String DISABLED_PLUGINS_GRADLE = "gradle.yaml";

    public static DisabledPluginsManager INSTANCE = new DisabledPluginsManager();

    public List<String> getDisabledPlugins(Path file) throws IOException {
        return parse(file).getDisabledPlugins();
    }

    @Override
    public DisabledPlugins parse(InputStream file) throws IOException {
        return MAPPER.readValue(file, DisabledPlugins.class);
    }

    @Override
    public void write(DisabledPlugins data, OutputStream out) throws IOException {
        MAPPER.writeValue(out, data);
    }
}
