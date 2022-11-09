package io.quarkus.platform.depstobuild;

import java.nio.file.Path;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ToolConfig {

    private static final String TOOL_CONFIG_DIR_NAME = ".deps-to-build";

    public Path getConfigDir() {
        return Path.of(System.getProperty("user.home")).resolve(TOOL_CONFIG_DIR_NAME).normalize().toAbsolutePath();
    }
}
