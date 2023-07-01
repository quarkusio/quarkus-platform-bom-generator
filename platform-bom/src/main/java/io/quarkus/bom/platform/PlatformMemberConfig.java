package io.quarkus.bom.platform;

import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlatformMemberConfig {

    private String name;
    private String bom;
    private DependencyManagementConfig dependencyManagement = new DependencyManagementConfig();
    private Boolean enabled;
    private Boolean hidden;
    private Boolean alignOwnConstraints;
    private Boolean keepThirdpartyExclusions;

    private PlatformMemberReleaseConfig release;

    private PlatformMemberDefaultTestConfig defaultTestConfig;
    private Collection<PlatformMemberTestConfig> tests = new ArrayList<>();
    private String testCatalogArtifact;

    private List<String> metadataOverrideFiles = new ArrayList<>(0);
    private List<String> metadataOverrideArtifacts = new ArrayList<>(0);

    private List<String> extensionGroupIds = new ArrayList<>(0);
    private List<String> ownGroupIds = new ArrayList<>(0);

    private RedHatExtensionDependencyCheck dependencyCheck;

    private ProjectDependencyFilterConfig depsToBuild;

    private SbomConfig sbom;

    public void applyOverrides(PlatformMemberConfig overrides) {
        if (overrides.bom != null) {
            bom = overrides.bom;
        }
        if (overrides.dependencyManagement != null) {
            if (dependencyManagement == null) {
                dependencyManagement = overrides.dependencyManagement;
            } else {
                dependencyManagement.applyOverrides(overrides.dependencyManagement);
            }
        }
        if (overrides.enabled != null) {
            enabled = overrides.enabled;
        }
        if (overrides.hidden != null) {
            hidden = overrides.hidden;
        }
        if (overrides.alignOwnConstraints != null) {
            alignOwnConstraints = overrides.alignOwnConstraints;
        }
        if (overrides.keepThirdpartyExclusions != null) {
            keepThirdpartyExclusions = overrides.keepThirdpartyExclusions;
        }
        if (overrides.release != null) {
            release.applyOverrides(overrides.release);
        }
        if (overrides.defaultTestConfig != null) {
            if (defaultTestConfig == null) {
                defaultTestConfig = overrides.defaultTestConfig;
            } else {
                defaultTestConfig.applyOverrides(overrides.defaultTestConfig);
            }
        }
        if (!overrides.tests.isEmpty()) {
            if (tests.isEmpty()) {
                tests = overrides.tests;
            } else {
                final Map<String, PlatformMemberTestConfig> map = new LinkedHashMap<>(tests.size() + overrides.tests.size());
                for (PlatformMemberTestConfig t : tests) {
                    map.put(t.getArtifact(), t);
                }
                for (PlatformMemberTestConfig t : overrides.tests) {
                    final PlatformMemberTestConfig original = map.put(t.getArtifact(), t);
                    if (original != null) {
                        original.applyOverrides(t);
                    }
                }
                tests = map.values();
            }
        }
        if (overrides.testCatalogArtifact != null) {
            testCatalogArtifact = overrides.testCatalogArtifact;
        }
        if (!overrides.metadataOverrideFiles.isEmpty()) {
            metadataOverrideFiles.addAll(overrides.metadataOverrideFiles);
        }
        if (!overrides.metadataOverrideArtifacts.isEmpty()) {
            metadataOverrideArtifacts.addAll(overrides.metadataOverrideArtifacts);
        }
        if (!overrides.extensionGroupIds.isEmpty()) {
            extensionGroupIds.addAll(overrides.extensionGroupIds);
        }
        if (!overrides.ownGroupIds.isEmpty()) {
            ownGroupIds.addAll(overrides.ownGroupIds);
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setBom(String bom) {
        this.bom = bom;
    }

    public String getBom() {
        return bom;
    }

    public void setDependencyManagement(DependencyManagementConfig dependencyManagement) {
        this.dependencyManagement = dependencyManagement;
    }

    public DependencyManagementConfig getDependencyManagement() {
        return dependencyManagement;
    }

    public void setRelease(PlatformMemberReleaseConfig release) {
        this.release = release;
    }

    public PlatformMemberReleaseConfig getRelease() {
        return release;
    }

    public void setTestCatalogArtifact(String testCatalogArtifact) {
        this.testCatalogArtifact = testCatalogArtifact;
    }

    public String getTestCatalogArtifact() {
        return testCatalogArtifact;
    }

    public void setDefaultTestConfig(PlatformMemberDefaultTestConfig defaultTestConfig) {
        this.defaultTestConfig = defaultTestConfig;
    }

    public PlatformMemberDefaultTestConfig getDefaultTestConfig() {
        return defaultTestConfig;
    }

    public void addTest(PlatformMemberTestConfig test) {
        tests.add(test);
    }

    public Collection<PlatformMemberTestConfig> getTests() {
        return tests;
    }

    public boolean hasTests() {
        return !tests.isEmpty() || testCatalogArtifact != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /**
     * Whether a member should be participating in the alignment but not be
     * installed and deployed.
     * 
     * @return true if the member should not be installed and deployed
     */
    public boolean isHidden() {
        return hidden != null && hidden;
    }

    /**
     * By default, only conflicting dependency constraints coming from
     * different members are aligned. This option allows to trigger alignment
     * of member own constraints.
     * 
     * @return true if the member should not be installed and deployed
     */
    public boolean isAlignOwnConstraints() {
        return alignOwnConstraints != null && alignOwnConstraints;
    }

    /**
     * Each member may configure its own exclusions for certain dependencies in their BOMs.
     * This option controls whether the exclusions for common dependencies should be aligned
     * for all the members or each member may keep their own exclusions for those common
     * dependencies.
     * The default value for this option is true.
     * 
     * @return true if the member chose to keep its exclusions for common dependencies
     */
    public boolean isKeepThirdpartyExclusions() {
        return keepThirdpartyExclusions == null || keepThirdpartyExclusions;
    }

    public void setMetadataOverrideFiles(List<String> metadataOverrideFiles) {
        this.metadataOverrideFiles = metadataOverrideFiles;
    }

    /**
     * Paths to JSON files containing extension catalog overrides.
     * 
     * @return paths to JSON files containing extension catalog overrides
     */
    public List<String> getMetadataOverrideFiles() {
        return metadataOverrideFiles;
    }

    public void setMetadataOverrideArtifacts(List<String> metadataOverrideArtifacts) {
        this.metadataOverrideArtifacts = metadataOverrideArtifacts;
    }

    /**
     * JSON Maven artifacts containing extension catalog overrides.
     * If both the artifacts and the {@link #metadataOverrideFiles} are configured,
     * the artifacts will be applied before the {@link #metadataOverrideFiles}.
     * 
     * @return JSON Maven artifacts containing extension catalog overrides
     */
    public List<String> getMetadataOverrideArtifacts() {
        return metadataOverrideArtifacts;
    }

    /**
     * A list of artifact groupIds of the member extensions which should be
     * included in the generated member descriptor.
     * 
     * @return list of extension artifact groupIds
     */
    public List<String> getExtensionGroupIds() {
        return extensionGroupIds;
    }

    public void setExtensionGroupIds(List<String> extensionGroupIds) {
        this.extensionGroupIds = extensionGroupIds;
    }

    /**
     * A list of artifact groupIds other members are not allowed to override
     * 
     * @return list of artifact groupIds other members are not allowed to override
     */
    public List<String> getOwnGroupIds() {
        return ownGroupIds;
    }

    public void setOwnGroupIds(List<String> ownGroupIds) {
        this.ownGroupIds = ownGroupIds;
    }

    public RedHatExtensionDependencyCheck getRedHatExtensionDependencyCheck() {
        return dependencyCheck;
    }

    public void setRedHatExtensionDependencyCheck(RedHatExtensionDependencyCheck dependencyCheck) {
        this.dependencyCheck = dependencyCheck;
    }

    public ArtifactCoords getPlatformBom(String defaultGroupId) {
        if (getRelease() == null || getRelease().getNext() == null) {
            final ArtifactCoords originalBom = ArtifactCoords.fromString(getBom());
            return ArtifactCoords.pom(defaultGroupId, originalBom.getArtifactId(), originalBom.getVersion());
        }
        var coords = ArtifactCoords.fromString(getRelease().getNext());
        return ArtifactCoords.TYPE_POM.equals(coords.getType()) ? coords
                : ArtifactCoords.pom(coords.getGroupId(), coords.getArtifactId(), coords.getVersion());
    }

    public ProjectDependencyFilterConfig getDependenciesToBuild() {
        return depsToBuild;
    }

    public void setDependenciesToBuild(ProjectDependencyFilterConfig depsToBuild) {
        this.depsToBuild = depsToBuild;
    }

    public SbomConfig getSbom() {
        return sbom;
    }

    public void setSbom(SbomConfig sbom) {
        this.sbom = sbom;
    }
}
