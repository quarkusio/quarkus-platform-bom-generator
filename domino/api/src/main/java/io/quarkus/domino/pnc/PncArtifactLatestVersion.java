package io.quarkus.domino.pnc;

import java.util.Objects;

public class PncArtifactLatestVersion {

    private String groupId;
    private String artifactId;
    private String version;
    private String latestVersion;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PncArtifactLatestVersion that = (PncArtifactLatestVersion) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(version, that.version) && Objects.equals(latestVersion, that.latestVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, latestVersion);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version + " latestVersion=" + latestVersion;
    }
}
