package io.quarkus.domino;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.aether.repository.RemoteRepository;

public class ReleaseRepo {

    final ScmRevision revision;
    final Map<ArtifactCoords, List<RemoteRepository>> artifacts = new HashMap<>();
    final Map<ScmRevision, ReleaseRepo> dependants = new HashMap<>();
    final Map<ScmRevision, ReleaseRepo> dependencies = new LinkedHashMap<>();

    ReleaseRepo(ScmRevision revision) {
        this.revision = revision;
    }

    /**
     * @deprecated for removal in favor of {@link #getRevision()}
     *
     * @return release id
     */
    @Deprecated(forRemoval = true)
    public ReleaseId id() {
        return revision;
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

    /** For debug purposes only. Do not use as a key in a {@link Map} or similar as this class is mutable. */
    @Override
    public String toString() {
        if (artifacts == null) {
            return "(" + revision.getRepository().getUrl() + ")";
        } else if (artifacts.size() == 1) {
            return artifacts.keySet().iterator().next().toCompactCoords() + " (" + revision.getRepository().getUrl() + ")";
        } else {
            return artifacts.keySet().stream()
                    .map(c -> c.getGroupId() + ":" + c.getVersion())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(",")) + " (" + revision.getRepository().getUrl() + ")";
        }
    }
}
