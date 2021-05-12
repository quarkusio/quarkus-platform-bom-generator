package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;

public class PlatformReleaseConfig {
    String platformKey;
    String stream;
    String version;
    List<String> members = new ArrayList<>();

    public String getPlatformKey() {
        return platformKey;
    }

    public String getStream() {
        return stream;
    }

    public String getVersion() {
        return version;
    }

    public void addMember(String member) {
        members.add(member);
    }

    public List<String> getMembers() {
        return members;
    }
}
