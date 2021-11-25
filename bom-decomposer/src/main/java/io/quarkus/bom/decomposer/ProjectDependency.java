package io.quarkus.bom.decomposer;

import io.quarkus.maven.ArtifactKey;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class ProjectDependency {

    public enum UpdateStatus {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }

    public static ProjectDependency create(ReleaseId releaseId, Artifact artifact) {
        return create(releaseId, new Dependency(artifact, null));
    }

    public static ProjectDependency create(ReleaseId releaseId, Dependency dep) {
        return new ProjectDependency(releaseId, dep);
    }

    protected final ReleaseId releaseId;
    protected final Artifact artifact;
    protected Dependency bomDependency;
    protected UpdateStatus updateStatus = UpdateStatus.UNKNOWN;
    protected ProjectDependency availableUpdate;
    protected boolean preferredVersion;
    private ArtifactKey key;

    private ProjectDependency(ReleaseId releaseId, Dependency dep) {
        this.releaseId = Objects.requireNonNull(releaseId);
        this.bomDependency = Objects.requireNonNull(dep);
        this.artifact = Objects.requireNonNull(dep.getArtifact());
    }

    public ReleaseId releaseId() {
        return releaseId;
    }

    public Dependency dependency() {
        return bomDependency;
    }

    public Artifact artifact() {
        return artifact;
    }

    public boolean isPreferredVersion() {
        return preferredVersion;
    }

    public UpdateStatus updateStatus() {
        return updateStatus;
    }

    public ProjectDependency availableUpdate() {
        return availableUpdate;
    }

    public boolean isUpdateAvailable() {
        return availableUpdate != null;
    }

    public void setAvailableUpdate(ProjectDependency update) {
        if (update != null) {
            updateStatus = UpdateStatus.AVAILABLE;
            availableUpdate = update;
        }
    }

    protected void setUpdateUnavailable() {
        this.updateStatus = UpdateStatus.UNAVAILABLE;
        this.availableUpdate = null;
    }

    public ArtifactKey key() {
        return key == null
                ? key = new ArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                        artifact.getExtension())
                : key;
    }

    @Override
    public String toString() {
        return artifact.toString();
    }
}
