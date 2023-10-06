package io.quarkus.domino;

import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.repository.RemoteRepository;

class ResolvedDependency implements DependencyTreeVisitor.DependencyVisit {

    private final ScmRevision revision;
    private final ArtifactCoords coords;
    private final List<RemoteRepository> repos;
    private final boolean managed;

    ResolvedDependency(ScmRevision revision, ArtifactCoords coords, List<RemoteRepository> repos, boolean managed) {
        this.revision = Objects.requireNonNull(revision, "Release ID is null");
        this.coords = Objects.requireNonNull(coords, "Artifact coordinates are null");
        this.repos = Objects.requireNonNull(repos, "Remote repositories are null");
        this.managed = managed;
    }

    @Override
    public ScmRevision getRevision() {
        return revision;
    }

    @Override
    public ArtifactCoords getCoords() {
        return coords;
    }

    @Override
    public List<RemoteRepository> getRepositories() {
        return repos;
    }

    @Override
    public boolean isManaged() {
        return managed;
    }
}
