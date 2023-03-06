package io.quarkus.domino;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.repository.RemoteRepository;

class ResolvedDependency implements DependencyTreeVisitor.DependencyVisit {

    private final ReleaseId releaseId;
    private final ArtifactCoords coords;
    private final List<RemoteRepository> repos;
    private final boolean managed;

    ResolvedDependency(ReleaseId releaseId, ArtifactCoords coords, List<RemoteRepository> repos, boolean managed) {
        this.releaseId = Objects.requireNonNull(releaseId, "Release ID is null");
        this.coords = Objects.requireNonNull(coords, "Artifact coordinates are null");
        this.repos = Objects.requireNonNull(repos, "Remote repositories are null");
        this.managed = managed;
    }

    @Override
    public ReleaseId getReleaseId() {
        return releaseId;
    }

    @Override
    public ArtifactCoords getCoords() {
        return coords;
    }

    public List<RemoteRepository> getRepositories() {
        return repos;
    }

    @Override
    public boolean isManaged() {
        return managed;
    }
}
