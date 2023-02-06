package io.quarkus.domino.gradle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

public class DependencyModelBuilder implements ParameterizedToolingModelBuilder<GradleProjectDependencyParameters> {

    private static final DependencyModelBuilder INSTANCE = new DependencyModelBuilder();

    public static DependencyModelBuilder getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canBuild(String modelName) {
        return GradleProjectDependencies.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        final ProjectDependencies result = new ProjectDependencies();
        final Set<Project> allProjects = project.getRootProject().getAllprojects();
        for (Project p : allProjects) {
            result.addModule(
                    collectModuleDeps(GradlePublishedModule.of(
                            p.getGroup().toString(), p.getName(), p.getVersion().toString(), p.getPath()),
                            p, Collections.emptySet()));
        }
        return result;
    }

    @Override
    public Object buildAll(String modelName, GradleProjectDependencyParameters params, Project project) {
        final ProjectDependencies result = new ProjectDependencies();
        final Map<String, GradlePublishedModule> publishedModules = getPublishedModules(params);
        final Set<ModuleId> moduleIds = getModuleIds(params);
        final Set<Project> allProjects = project.getRootProject().getAllprojects();
        for (Project p : allProjects) {
            final GradlePublishedModule publishedModule = publishedModules.get(p.getPath());
            if (publishedModule != null) {
                result.addModule(collectModuleDeps(publishedModule, p, moduleIds));
            }
        }
        return result;
    }

    private ModuleDependencies collectModuleDeps(GradlePublishedModule publishedModule, Project project,
            Set<ModuleId> moduleIds) {
        final ModuleDependencies module = ModuleDependencies.of(publishedModule.getGroup(), publishedModule.getName(),
                publishedModule.getVersion());
        final Configuration config = project.getConfigurations().findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        if (config != null) {
            for (ResolvedDependency d : config.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
                module.addDependencies(toGradleDependencies(d, moduleIds));
            }
        }
        return module;
    }

    private List<GradleDependency> toGradleDependencies(ResolvedDependency parent, Set<ModuleId> moduleIds) {
        final List<GradleDependency> result;
        DependencyNode node = null;
        final Set<ResolvedArtifact> moduleArtifacts = parent.getModuleArtifacts();
        if (moduleArtifacts.isEmpty()) {
            // it's not guaranteed to be a POM but it's not clear how to figure out the type in this case
            node = DependencyNode.of(parent.getModuleGroup(), parent.getModuleName(), "", "pom", parent.getModuleVersion());
            result = List.of(node);
        } else {
            result = new ArrayList<>(moduleArtifacts.size());
            for (ResolvedArtifact a : parent.getModuleArtifacts()) {
                node = toDependencyNode(a, moduleIds);
                result.add(node);
            }
        }
        for (ResolvedDependency c : parent.getChildren()) {
            node.addDependencies(toGradleDependencies(c, moduleIds));
        }
        return result;
    }

    private static DependencyNode toDependencyNode(ResolvedArtifact a, Set<ModuleId> moduleIds) {
        final String group = a.getModuleVersion().getId().getGroup();
        final String version = a.getModuleVersion().getId().getVersion();
        if (moduleIds.contains(new ModuleId(group, a.getName(), version))) {
            // we may need to replace some dependencies with general GAVs of modules producing those artifacts 
            return DependencyNode.of(group, a.getName(), "", "jar", version);
        }
        return DependencyNode.of(group, a.getName(), a.getClassifier(), a.getType(),
                version);
    }

    private static Map<String, GradlePublishedModule> getPublishedModules(GradleProjectDependencyParameters params) {
        final Collection<GradlePublishedModule> projectPaths = params.getModules();
        final Map<String, GradlePublishedModule> publishedModules = new HashMap<>(projectPaths.size());
        for (GradlePublishedModule m : projectPaths) {
            publishedModules.put(m.getProjectPath(), m);
        }
        return publishedModules;
    }

    private static Set<ModuleId> getModuleIds(GradleProjectDependencyParameters params) {
        return params.getModules().stream().map(m -> new ModuleId(m.getGroup(), m.getName(), m.getVersion()))
                .collect(Collectors.toSet());
    }

    @Override
    public Class<GradleProjectDependencyParameters> getParameterType() {
        return GradleProjectDependencyParameters.class;
    }

    private static class ModuleId {

        final String group;
        final String name;
        final String version;

        ModuleId(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, name, version);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ModuleId other = (ModuleId) obj;
            return Objects.equals(group, other.group) && Objects.equals(name, other.name)
                    && Objects.equals(version, other.version);
        }
    }
}
