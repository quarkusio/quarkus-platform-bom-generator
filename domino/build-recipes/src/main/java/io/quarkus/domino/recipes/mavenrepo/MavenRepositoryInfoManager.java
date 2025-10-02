package io.quarkus.domino.recipes.mavenrepo;

import io.quarkus.domino.recipes.RecipeManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MavenRepositoryInfoManager implements RecipeManager<MavenRepositoryInfo> {

    public static MavenRepositoryInfoManager INSTANCE = new MavenRepositoryInfoManager();

    @Override
    public MavenRepositoryInfo parse(InputStream file) throws IOException {
        return MAPPER.readValue(file, MavenRepositoryInfo.class);
    }

    @Override
    public void write(MavenRepositoryInfo data, OutputStream out) throws IOException {
        MAPPER.writeValue(out, data);
    }
}
