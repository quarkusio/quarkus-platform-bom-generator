package io.quarkus.domino.gradle;

import java.util.List;

public interface GradleDependency {

    String getGroupId();

    String getArtifactId();

    String getClassifier();

    String getType();

    String getVersion();

    List<GradleDependency> getDependencies();
}
