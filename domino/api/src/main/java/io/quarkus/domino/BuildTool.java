package io.quarkus.domino;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public enum BuildTool {

    MAVEN(List.of("pom.xml")),
    GRADLE(List.of("build.gradle", "build.gradle.kts"));

    private final Collection<String> buildFiles;

    BuildTool(Collection<String> buildFile) {
        this.buildFiles = buildFile;
    }

    public boolean canBuild(Path dir) {
        for (String s : buildFiles) {
            if (Files.exists(dir.resolve(s))) {
                return true;
            }
        }
        return false;
    }

    public static BuildTool forProjectDir(Path projectDir) {
        if (Files.isDirectory(projectDir)) {
            for (BuildTool bt : BuildTool.values()) {
                if (bt.canBuild(projectDir)) {
                    return bt;
                }
            }
        }
        throw new IllegalArgumentException("Unable to find a determine the build tool for " + projectDir);
    }
}
