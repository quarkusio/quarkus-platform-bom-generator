package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlatformMemberConfig {

    String name;
    String bom;
    List<String> dependencyManagement = Collections.emptyList();
    boolean disabled;

    PlatformMemberReleaseConfig release;

    List<String> tests = new ArrayList<>();
}
