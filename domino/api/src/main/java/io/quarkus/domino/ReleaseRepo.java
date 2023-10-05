package io.quarkus.domino;

import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.repository.RemoteRepository;

public class ReleaseRepo {

    final ScmRevision revision;
    final Map<ArtifactCoords, List<RemoteRepository>> artifacts = new HashMap<>();
    final Map<ScmRevision, ReleaseRepo> dependants = new HashMap<>();
    final Map<ScmRevision, ReleaseRepo> dependencies = new LinkedHashMap<>();

    ReleaseRepo(ScmRevision revision) {
        this.revision = revision;
    }

    public ScmRevision getRevision() {
        return revision;
    }

    public Map<ArtifactCoords, List<RemoteRepository>> getArtifacts() {
        return artifacts;
    }

    public Collection<ReleaseRepo> getDependencies() {
        return dependencies.values();
    }

    void addRepoDependency(ReleaseRepo repo) {
        if (repo != this) {
            dependencies.putIfAbsent(repo.getRevision(), repo);
            repo.dependants.putIfAbsent(getRevision(), this);
        }
    }

    public boolean isRoot() {
        return dependants.isEmpty();
    }
}
