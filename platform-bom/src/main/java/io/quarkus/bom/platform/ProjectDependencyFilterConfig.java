package io.quarkus.bom.platform;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.util.HashSet;
import java.util.Set;

public class ProjectDependencyFilterConfig {

    private Set<String> excludeGroupIds = Set.of();
    private Set<ArtifactKey> excludeKeys = Set.of();
    private Set<ArtifactCoords> excludeArtifacts = Set.of();
    private Set<String> includeGroupIds = Set.of();
    private Set<ArtifactKey> includeKeys = Set.of();
    private Set<ArtifactCoords> includeArtifacts = Set.of();
    private Set<String> hideArtifacts = Set.of();

    private static <T> Set<T> mergeIn(Set<T> s1, Set<T> s2) {
        if (s1.isEmpty()) {
            if (s2.isEmpty()) {
                return Set.of();
            }
            return new HashSet<>(s2);
        }
        if (!s2.isEmpty()) {
            s1.addAll(s2);
        }
        return s1;
    }

    public void merge(ProjectDependencyFilterConfig other) {
        excludeGroupIds = mergeIn(excludeGroupIds, other.excludeGroupIds);
        excludeKeys = mergeIn(excludeKeys, other.excludeKeys);
        excludeArtifacts = mergeIn(excludeArtifacts, other.excludeArtifacts);
        includeGroupIds = mergeIn(includeGroupIds, other.includeGroupIds);
        includeKeys = mergeIn(includeKeys, other.includeKeys);
        includeArtifacts = mergeIn(includeArtifacts, other.includeArtifacts);
        hideArtifacts = mergeIn(hideArtifacts, other.hideArtifacts);
    }

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

    public Set<String> getHideArtifacts() {
        return hideArtifacts;
    }

    public void setHideArtifacts(Set<String> hideArtifacts) {
        this.hideArtifacts = hideArtifacts;
    }
}
