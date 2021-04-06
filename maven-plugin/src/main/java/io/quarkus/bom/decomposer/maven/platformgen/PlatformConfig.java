package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.Collections;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

public class PlatformConfig {

    @Parameter(required = true)
    String bom;

    @Parameter
    boolean skipInstall;

    PlatformStackConfig platformStack;

    @Parameter(required = true)
    PlatformMemberConfig core;

    List<PlatformMemberConfig> members = Collections.emptyList();

    PlatformBomGeneratorConfig bomGenerator;

    PlatformDescriptorGeneratorConfig descriptorGenerator;
}
