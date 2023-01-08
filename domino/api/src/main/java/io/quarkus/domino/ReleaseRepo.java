package io.quarkus.domino;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.repository.RemoteRepository;

public class ReleaseRepo {

    final ReleaseId id;
    final Map<ArtifactCoords, List<RemoteRepository>> artifacts = new HashMap<>();
    final Map<ReleaseId, ReleaseRepo> dependants = new HashMap<>();
    final Map<ReleaseId, ReleaseRepo> dependencies = new LinkedHashMap<>();

    ReleaseRepo(ReleaseId release) {
        this.id = release;
    }

    public ReleaseId id() {
        return id;
    }

    public Map<ArtifactCoords, List<RemoteRepository>> getArtifacts() {
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
