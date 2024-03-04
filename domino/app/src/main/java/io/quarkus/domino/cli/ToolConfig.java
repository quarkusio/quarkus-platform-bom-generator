package io.quarkus.domino.cli;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;

@ApplicationScoped
public class ToolConfig {

    private static final String TOOL_CONFIG_DIR_NAME = ".domino";

    public Path getConfigDir() {
        return Path.of(System.getProperty("user.home")).resolve(TOOL_CONFIG_DIR_NAME).normalize().toAbsolutePath();
    }
}
