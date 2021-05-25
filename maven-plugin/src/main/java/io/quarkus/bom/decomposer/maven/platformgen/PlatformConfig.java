package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.Collections;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

public class PlatformConfig {

    @Parameter(required = true)
    String bom;

    @Parameter(required = true)
    boolean generatePlatformProperties = true;

    @Parameter
    boolean skipInstall;

    PlatformReleaseConfig platformRelease;

    @Parameter(required = true)
    PlatformMemberConfig core;

    List<PlatformMemberConfig> members = Collections.emptyList();

    PlatformBomGeneratorConfig bomGenerator;

    PlatformDescriptorGeneratorConfig descriptorGenerator;

    AttachedMavenPluginConfig attachedMavenPlugin;
}
