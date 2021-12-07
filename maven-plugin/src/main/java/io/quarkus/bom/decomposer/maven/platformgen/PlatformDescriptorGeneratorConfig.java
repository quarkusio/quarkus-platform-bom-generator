package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;

public class PlatformDescriptorGeneratorConfig {

    /** file used for overrides - overridesFiles takes precedence over this file. **/
    @Parameter(property = "overridesFile", defaultValue = "${project.basedir}/src/main/resources/extensions-overrides.json")
    String overridesFile;

    Set<String> ignoredGroupIds = new HashSet<>(0);

    List<String> ignoredArtifacts = new ArrayList<>(0);

    Set<String> processGroupIds = new HashSet<>(1);

    boolean skipCategoryCheck;

    boolean resolveDependencyManagement;
}
