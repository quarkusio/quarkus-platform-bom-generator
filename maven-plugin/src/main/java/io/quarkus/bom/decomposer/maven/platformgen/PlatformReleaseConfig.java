package io.quarkus.bom.decomposer.maven.platformgen;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PlatformReleaseConfig {
    private String platformKey;
    private String stream;
    private String version;

    private boolean onlyChangedMembers;

    public String getPlatformKey() {
        return platformKey;
    }

    public void setPlatformKey(String platformKey) {
        this.platformKey = platformKey;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @JsonIgnore
    public boolean isOnlyChangedMembers() {
        return onlyChangedMembers;
    }

    public void setOnlyChangedMembers(boolean onlyChangedMembers) {
        this.onlyChangedMembers = onlyChangedMembers;
    }
}
