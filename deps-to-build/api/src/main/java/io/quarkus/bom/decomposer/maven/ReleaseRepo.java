package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReleaseRepo {

    final ReleaseId id;
    final List<ArtifactCoords> artifacts = new ArrayList<>();
    final Map<ReleaseId, ReleaseRepo> dependants = new HashMap<>();
    final Map<ReleaseId, ReleaseRepo> dependencies = new LinkedHashMap<>();

    ReleaseRepo(ReleaseId release) {
        this.id = release;
    }

    public ReleaseId id() {
        return id;
    }

    public List<ArtifactCoords> getArtifacts() {
        return artifacts;
    }

    public Collection<ReleaseRepo> getDependencies() {
        return dependencies.values();
    }

    void addRepoDependency(ReleaseRepo repo) {
        if (repo != this) {
            dependencies.putIfAbsent(repo.id(), repo);
            repo.dependants.putIfAbsent(id(), this);
        }
    }

    public boolean isRoot() {
        return dependants.isEmpty();
    }
}
