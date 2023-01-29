package io.quarkus.domino.gradle;

import java.util.Collection;

public interface GradleProjectDependencyParameters {

    Collection<GradlePublishedModule> getModules();

    void setModules(Collection<GradlePublishedModule> modules);
}
