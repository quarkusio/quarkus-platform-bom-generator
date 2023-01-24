package io.quarkus.domino.gradle;

import java.util.Collection;

public interface GradleProjectDependencies {

    Collection<GradleModuleDependencies> getModules();
}
