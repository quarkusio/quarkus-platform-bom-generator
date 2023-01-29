package io.quarkus.domino.gradle;

import java.util.List;

public interface GradleModuleDependencies {

    String getGroup();

    String getName();

    String getVersion();

    List<GradleDependency> getDependencies();
}
