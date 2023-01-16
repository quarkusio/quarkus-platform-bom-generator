package io.quarkus.bom.decomposer.maven.platformgen;

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

    @Parameter(defaultValue = "true")
    boolean enableNonMemberQuarkiverseExtensions = true;

    @Parameter
    List<String> versionConstraintPreferences = new ArrayList<>(0);

    /**
     * This option disables automatic alignment to preferred versions (according to the {@link #versionConstraintPreferences})
     * artifacts that appear to have the same origin as some other artifacts that match preferred versions (according to the
     * {@link #versionConstraintPreferences})
     */
    @Parameter
    boolean disableGroupAlignmentToPreferredVersions;

    /**
     * @deprecated in favor of {@link #foreignPreferredConstraint}
     */
    @Parameter
    @Deprecated(forRemoval = true, since = "0.0.68")
    String notPreferredQuarkusBomConstraint;

    String foreignPreferredConstraint;
}
