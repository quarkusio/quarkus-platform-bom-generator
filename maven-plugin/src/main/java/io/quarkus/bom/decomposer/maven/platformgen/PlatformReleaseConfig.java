package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;

public class PlatformReleaseConfig {
    private String platformKey;
    private String stream;
    private String version;
    private List<String> members = new ArrayList<>();

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

    public void addMember(String member) {
        members.add(member);
    }

    public List<String> getMembers() {
        return members;
    }
}
