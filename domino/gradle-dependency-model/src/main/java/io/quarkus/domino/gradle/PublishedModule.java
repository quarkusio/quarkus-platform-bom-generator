package io.quarkus.domino.gradle;

import java.util.Objects;

class PublishedModule implements GradlePublishedModule {

    private final String group;
    private final String name;
    private final String version;
    private final String projectPath;

    PublishedModule(String group, String name, String version, String projectPath) {
        super();
        this.group = group;
        this.name = name;
        this.version = version;
        this.projectPath = projectPath;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version, projectPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!GradlePublishedModule.class.isAssignableFrom(obj.getClass()))
            return false;
        GradlePublishedModule other = (GradlePublishedModule) obj;
        return Objects.equals(group, other.getGroup()) && Objects.equals(name, other.getName())
                && Objects.equals(version, other.getVersion())
                && Objects.equals(projectPath, other.getProjectPath());
    }

    @Override
    public String toString() {
        return group + ":" + name + ":" + version;
    }
}
