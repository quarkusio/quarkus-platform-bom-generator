package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.List;

public class PlatformMemberConfig {

    String name;
    String bom;
    boolean disabled;

    PlatformMemberReleaseConfig release;

    List<String> tests = new ArrayList<>();
}
