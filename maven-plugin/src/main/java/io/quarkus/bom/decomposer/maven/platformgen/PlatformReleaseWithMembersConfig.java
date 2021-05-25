package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;

public class PlatformReleaseWithMembersConfig extends PlatformReleaseConfig {

    private List<String> members = new ArrayList<>();

    public void addMember(String member) {
        members.add(member);
    }

    public List<String> getMembers() {
        return members;
    }
}
