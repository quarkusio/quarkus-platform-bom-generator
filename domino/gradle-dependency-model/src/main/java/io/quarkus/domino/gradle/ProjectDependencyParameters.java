package io.quarkus.domino.gradle;

import java.util.Collection;
import java.util.Objects;

class ProjectDependencyParameters implements GradleProjectDependencyParameters {

    private Collection<GradlePublishedModule> modules;

    ProjectDependencyParameters(Collection<GradlePublishedModule> modules) {
        super();
        this.modules = modules;
    }

    public Collection<GradlePublishedModule> getModules() {
        return modules;
    }

    @Override
    public void setModules(Collection<GradlePublishedModule> modules) {
        this.modules = modules;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modules);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProjectDependencyParameters other = (ProjectDependencyParameters) obj;
        return Objects.equals(modules, other.modules);
    }
}
