package io.quarkus.platform.generator;

public interface PlatformTestProjectBuilder {

    PlatformBuildResult build(String... args);
}
