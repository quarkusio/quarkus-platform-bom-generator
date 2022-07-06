package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.util.HashSet;
import java.util.Set;

public class DependenciesToBuildConfig {

    private Set<String> excludeGroupIds = Set.of();
    private Set<ArtifactKey> excludeKeys = Set.of();
    private Set<ArtifactCoords> excludeArtifacts = Set.of();
    private Set<String> includeGroupIds = Set.of();
    private Set<ArtifactKey> includeKeys = Set.of();
    private Set<ArtifactCoords> includeArtifacts = Set.of();

    public Set<String> getExcludeGroupIds() {
        return excludeGroupIds;
    }

    public void setExcludeGroupIds(Set<String> excludeGroupIds) {
        this.excludeGroupIds = excludeGroupIds;
    }

    public Set<ArtifactKey> getExcludeKeys() {
        return excludeKeys;
    }

    public void setExcludeKeys(Set<String> excludeKeys) {
        this.excludeKeys = new HashSet<>(excludeKeys.size());
        excludeKeys.forEach(s -> this.excludeKeys.add(ArtifactKey.fromString(s)));
    }

    public Set<ArtifactCoords> getExcludeArtifacts() {
        return excludeArtifacts;
    }

    public void setExcludeArtifacts(Set<String> excludeArtifacts) {
        this.excludeArtifacts = new HashSet<>(excludeArtifacts.size());
        excludeArtifacts.forEach(s -> this.excludeArtifacts.add(ArtifactCoords.fromString(s)));
    }

    public Set<String> getIncludeGroupIds() {
        return includeGroupIds;
    }

    public void setIncludeGroupIds(Set<String> includeGroupIds) {
        this.includeGroupIds = includeGroupIds;
    }

    public Set<ArtifactKey> getIncludeKeys() {
        return includeKeys;
    }

    public void setIncludeKeys(Set<String> includeKeys) {
        this.includeKeys = new HashSet<>(includeKeys.size());
        includeKeys.forEach(s -> this.includeKeys.add(ArtifactKey.fromString(s)));
    }

    public Set<ArtifactCoords> getIncludeArtifacts() {
        return includeArtifacts;
    }

    public void setIncludeArtifacts(Set<String> includeArtifacts) {
        this.includeArtifacts = new HashSet<>(includeArtifacts.size());
        includeArtifacts.forEach(s -> this.includeArtifacts.add(ArtifactCoords.fromString(s)));
    }
}
