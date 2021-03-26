package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;

public class PlatformStackConfig {
    String key;
    String version;
    List<String> members = new ArrayList<>();

    public String getKey() {
        return key;
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
