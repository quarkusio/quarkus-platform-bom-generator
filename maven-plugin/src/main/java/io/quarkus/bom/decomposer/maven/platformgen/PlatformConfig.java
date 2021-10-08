package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.decomposer.maven.GenerateMavenRepoZip;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlatformConfig {

    private PlatformReleaseConfig release;

    private UniversalPlatformConfig universal;

    private PlatformMemberConfig core;

    private Collection<PlatformMemberConfig> members;

    private PlatformBomGeneratorConfig bomGenerator;

    private PlatformDescriptorGeneratorConfig descriptorGenerator;

    private AttachedMavenPluginConfig attachedMavenPlugin;

    private String upstreamQuarkusCoreVersion;

    private GenerateMavenRepoZip generateMavenRepoZip;

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

    public Collection<PlatformMemberConfig> getMembers() {
        return members;
    }

    public void setMembers(Collection<PlatformMemberConfig> members) {
        final Map<String, PlatformMemberConfig> map = new LinkedHashMap<>(members.size());
        for (PlatformMemberConfig member : members) {
            final PlatformMemberConfig original = map.putIfAbsent(member.getName(), member);
            if (original != null) {
                original.applyOverrides(member);
            }
        }
        this.members = map.values();
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

    public String getUpstreamQuarkusCoreVersion() {
        return upstreamQuarkusCoreVersion;
    }

    public void setUpstreamQuarkusCoreVersion(String upstreamQuarkusCoreVersion) {
        this.upstreamQuarkusCoreVersion = upstreamQuarkusCoreVersion;
    }

    public boolean hasUpstreamQuarkusCoreVersion() {
        return upstreamQuarkusCoreVersion != null && !upstreamQuarkusCoreVersion.isBlank();
    }

    public GenerateMavenRepoZip getGenerateMavenRepoZip() {
        return generateMavenRepoZip;
    }

    public void setGenerateMavenRepoZip(GenerateMavenRepoZip generateMavenRepoZip) {
        this.generateMavenRepoZip = generateMavenRepoZip;
    }
}
