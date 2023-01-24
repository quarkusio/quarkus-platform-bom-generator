package io.quarkus.domino.gradle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class DependencyNode implements GradleDependency, Serializable {

    static DependencyNode of(String groupId, String artifactId, String classifier, String type, String version) {
        return new DependencyNode(groupId, artifactId, classifier, type, version);
    }

    private final String groupId;
    private final String artifactId;
    private final String classifier;
    private final String type;
    private final String version;
    private final List<GradleDependency> deps = new ArrayList<>();

    DependencyNode(String groupId, String artifactId, String classifier, String type, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier == null ? "" : classifier;
        this.type = type;
        this.version = version;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + classifier + ":" + type + ":" + version;
    }

    @Override
    public List<GradleDependency> getDependencies() {
        return deps;
    }

    void addDependency(GradleDependency d) {
        deps.add(d);
    }

    void addDependencies(Collection<GradleDependency> deps) {
        this.deps.addAll(deps);
    }
}
