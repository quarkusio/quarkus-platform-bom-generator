package io.quarkus.bom.decomposer.maven;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GenerateMavenRepoZip {

    private String bom;

    private String repositoryDir;

    private String zipLocation;

    private Set<String> excludedGroupIds = Collections.emptySet();

    private Set<String> excludedArtifacts = Collections.emptySet();

    private List<String> extraArtifacts = Collections.emptyList();

    private String includedVersionsPattern;

    public String getBom() {
        return bom;
    }

    public void setBom(String bom) {
        this.bom = bom;
    }

    public String getRepositoryDir() {
        return repositoryDir;
    }

    public void setRepositoryDir(String repositoryDir) {
        this.repositoryDir = repositoryDir;
    }

    public String getZipLocation() {
        return zipLocation;
    }

    public void setZipLocation(String zipLocation) {
        this.zipLocation = zipLocation;
    }

    public Set<String> getExcludedGroupIds() {
        return excludedGroupIds;
    }

    public void setExcludedGroupIds(Set<String> excludedGroupIds) {
        this.excludedGroupIds = excludedGroupIds;
    }

    public Set<String> getExcludedArtifacts() {
        return excludedArtifacts;
    }

    public void setExcludedArtifacts(Set<String> excludedArtifacts) {
        this.excludedArtifacts = excludedArtifacts;
    }

    public List<String> getExtraArtifacts() {
        return extraArtifacts;
    }

    public void setExtraArtifacts(List<String> extraArtifacts) {
        this.extraArtifacts = extraArtifacts;
    }

    public String getIncludedVersionsPattern() {
        return includedVersionsPattern;
    }

    public void setIncludedVersionsPattern(String includedVersionPattern) {
        this.includedVersionsPattern = includedVersionPattern;
    }
}
