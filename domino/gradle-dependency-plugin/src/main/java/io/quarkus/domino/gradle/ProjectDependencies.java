package io.quarkus.domino.gradle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ProjectDependencies implements GradleProjectDependencies, Serializable {

    private List<GradleModuleDependencies> modules = new ArrayList<>();

    @Override
    public Collection<GradleModuleDependencies> getModules() {
        return modules;
    }

    void addModule(GradleModuleDependencies module) {
        modules.add(module);
    }
}
