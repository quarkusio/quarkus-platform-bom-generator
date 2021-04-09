package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;

public class PlatformStackConfig {
    String stream;
    String version;
    List<String> members = new ArrayList<>();

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
