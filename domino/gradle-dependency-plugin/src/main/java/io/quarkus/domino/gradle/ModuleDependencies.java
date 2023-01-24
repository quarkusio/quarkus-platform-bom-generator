package io.quarkus.domino.gradle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ModuleDependencies implements GradleModuleDependencies, Serializable {

    static ModuleDependencies of(String group, String name, String version) {
        return new ModuleDependencies(group, name, version);
    }

    private final String group;
    private final String name;
    private final String version;
    private final List<GradleDependency> deps = new ArrayList<>();

    ModuleDependencies(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
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
    public List<GradleDependency> getDependencies() {
        return deps;
    }

    void addDependency(GradleDependency dep) {
        deps.add(dep);
    }

    void addDependencies(Collection<GradleDependency> deps) {
        this.deps.addAll(deps);
    }

    @Override
    public String toString() {
        return group + ":" + name + ":" + version;
    }
}
