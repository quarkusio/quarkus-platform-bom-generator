package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.HashSet;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;

public class PlatformBomGeneratorConfig {

    @Parameter
    protected Set<String> enforcedDependencies = new HashSet<>(0);

    @Parameter
    protected Set<String> excludedDependencies = new HashSet<>(0);

    @Parameter
    protected Set<String> excludedGroups = new HashSet<>(0);

}
