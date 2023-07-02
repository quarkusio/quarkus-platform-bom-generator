package io.quarkus.platform.generator.builder;

import io.quarkus.platform.generator.PlatformBuildResult;
import io.quarkus.platform.generator.PlatformTestProjectBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MavenInvokerPlatformTestProjectBuilder implements PlatformTestProjectBuilder {

    public static MavenInvokerPlatformTestProjectBuilder getInstance() {
        return new MavenInvokerPlatformTestProjectBuilder();
    }

    private Path projectDir;
    private Path platformConfigModule;

    public MavenInvokerPlatformTestProjectBuilder setProjectDir(Path projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public MavenInvokerPlatformTestProjectBuilder setPlatformModule(Path projectDir) {
        this.platformConfigModule = projectDir;
        return this;
    }

    @Override
    public PlatformBuildResult build(String... args) {
        final Properties props = new Properties();
        props.setProperty("workspaceDiscovery", "true");
        RunningInvoker invoker = new RunningInvoker(projectDir.toFile(), false);
        try {
            invoker.execute(List.of(args), Map.of(), props).getProcess().waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return PlatformBuildResult.load(platformConfigModule);
    }
}
