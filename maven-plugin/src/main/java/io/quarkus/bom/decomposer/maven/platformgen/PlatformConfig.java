package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.Collections;
import java.util.List;

public class PlatformConfig {

    private PlatformReleaseConfig release;

    private UniversalPlatformConfig universal;

    private PlatformMemberConfig core;

    private List<PlatformMemberConfig> members = Collections.emptyList();

    private PlatformBomGeneratorConfig bomGenerator;

    private PlatformDescriptorGeneratorConfig descriptorGenerator;

    private AttachedMavenPluginConfig attachedMavenPlugin;

    public PlatformReleaseConfig getRelease() {
        return release;
    }

    public void setRelease(PlatformReleaseConfig platformRelease) {
        this.release = platformRelease;
    }

    public UniversalPlatformConfig getUniversal() {
        return universal == null ? universal = new UniversalPlatformConfig() : universal;
    }

    public void setUniversal(UniversalPlatformConfig universal) {
        this.universal = universal;
    }

    public PlatformMemberConfig getCore() {
        return core;
    }

    public void setCore(PlatformMemberConfig core) {
        this.core = core;
    }

    public List<PlatformMemberConfig> getMembers() {
        return members;
    }

    public void setMembers(List<PlatformMemberConfig> members) {
        this.members = members;
    }

    public PlatformBomGeneratorConfig getBomGenerator() {
        return bomGenerator;
    }

    public void setBomGenerator(PlatformBomGeneratorConfig bomGenerator) {
        this.bomGenerator = bomGenerator;
    }

    public PlatformDescriptorGeneratorConfig getDescriptorGenerator() {
        return descriptorGenerator;
    }

    public void setDescriptorGenerator(PlatformDescriptorGeneratorConfig descriptorGenerator) {
        this.descriptorGenerator = descriptorGenerator;
    }

    public AttachedMavenPluginConfig getAttachedMavenPlugin() {
        return attachedMavenPlugin;
    }

    public void setAttachedMavenPlugin(AttachedMavenPluginConfig attachedMavenPlugin) {
        this.attachedMavenPlugin = attachedMavenPlugin;
    }
}
