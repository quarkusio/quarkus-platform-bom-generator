package io.quarkus.bom.decomposer;

import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bootstrap.model.AppArtifactKey;

public class ProjectDependency {

	public enum UpdateStatus {
		UNKNOWN, AVAILABLE, UNAVAILABLE
	}

	public static ProjectDependency create(ReleaseId releaseId, Artifact artifact) {
		return new ProjectDependency(releaseId, artifact);
	}

	protected final ReleaseId releaseId;
	protected final Artifact artifact;
	protected UpdateStatus updateStatus = UpdateStatus.UNKNOWN;
	protected ProjectDependency availableUpdate;
	protected boolean preferredVersion;
	private AppArtifactKey key;

	private ProjectDependency(ReleaseId releaseId, Artifact artifact) {
		this.releaseId = Objects.requireNonNull(releaseId);
		this.artifact = Objects.requireNonNull(artifact);
	}

	public ReleaseId releaseId() {
		return releaseId;
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
		if(update != null) {
			updateStatus = UpdateStatus.AVAILABLE;
			availableUpdate = update;
		}
	}

	protected void setUpdateUnavailable() {
		this.updateStatus = UpdateStatus.UNAVAILABLE;
		this.availableUpdate = null;
	}

	public AppArtifactKey key() {
		return key == null ? key = new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension()) : key;
	}

	@Override
	public String toString() {
		return artifact.toString();
	}
}
