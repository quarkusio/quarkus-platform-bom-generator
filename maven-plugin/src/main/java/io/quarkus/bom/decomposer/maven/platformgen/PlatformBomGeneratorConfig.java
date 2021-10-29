package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.platform.NotPreferredQuarkusBomConstraint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;

public class PlatformBomGeneratorConfig {

    @Parameter
    Set<String> enforcedDependencies = new HashSet<>(0);

    @Parameter
    Set<String> excludedDependencies = new HashSet<>(0);

    @Parameter
    Set<String> excludedGroups = new HashSet<>(0);

    @Parameter
    boolean enableNonMemberQuarkiverseExtensions;

    @Parameter
    List<String> versionConstraintPreferences = new ArrayList<>(0);

    @Parameter
    NotPreferredQuarkusBomConstraint notPreferredQuarkusBomConstraint = NotPreferredQuarkusBomConstraint.ERROR;
}
