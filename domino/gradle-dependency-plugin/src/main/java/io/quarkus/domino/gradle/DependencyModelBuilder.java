package io.quarkus.domino.gradle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                            p));
        }
        return result;
    }

    @Override
    public Object buildAll(String modelName, GradleProjectDependencyParameters params, Project project) {
        final ProjectDependencies result = new ProjectDependencies();
        final Map<String, GradlePublishedModule> publishedModules = getPublishedModules(params);
        final Set<Project> allProjects = project.getRootProject().getAllprojects();
        for (Project p : allProjects) {
            final GradlePublishedModule publishedModule = publishedModules.get(p.getPath());
            if (publishedModule != null) {
                result.addModule(collectModuleDeps(publishedModule, p));
            }
        }
        return result;
    }

    private ModuleDependencies collectModuleDeps(GradlePublishedModule publishedModule, Project project) {
        final ModuleDependencies module = ModuleDependencies.of(publishedModule.getGroup(), publishedModule.getName(),
                publishedModule.getVersion());
        final Configuration config = project.getConfigurations().findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        if (config != null) {
            for (ResolvedDependency d : config.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
                module.addDependencies(toGradleDependencies(d));
            }
        }
        return module;
    }

    private List<GradleDependency> toGradleDependencies(ResolvedDependency parent) {
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
                node = toDependencyNode(a);
                result.add(node);
            }
        }
        for (ResolvedDependency c : parent.getChildren()) {
            node.addDependencies(toGradleDependencies(c));
        }
        return result;
    }

    private static DependencyNode toDependencyNode(ResolvedArtifact a) {
        return DependencyNode.of(a.getModuleVersion().getId().getGroup(), a.getName(), a.getClassifier(), a.getType(),
                a.getModuleVersion().getId().getVersion());
    }

    private static Map<String, GradlePublishedModule> getPublishedModules(GradleProjectDependencyParameters params) {
        final Collection<GradlePublishedModule> projectPaths = params.getModules();
        final Map<String, GradlePublishedModule> publishedModules = new HashMap<>(projectPaths.size());
        for (GradlePublishedModule m : projectPaths) {
            publishedModules.put(m.getProjectPath(), m);
        }
        return publishedModules;
    }

    @Override
    public Class<GradleProjectDependencyParameters> getParameterType() {
        return GradleProjectDependencyParameters.class;
    }
}
