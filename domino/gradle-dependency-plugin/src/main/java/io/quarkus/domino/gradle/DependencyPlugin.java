package io.quarkus.domino.gradle;

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

public class DependencyPlugin implements Plugin<Project> {

    private final ToolingModelBuilderRegistry modelBuilderRegistry;

    @Inject
    public DependencyPlugin(ToolingModelBuilderRegistry modelBuilderRegistry) {
        this.modelBuilderRegistry = modelBuilderRegistry;
    }

    public void apply(Project project) {
        modelBuilderRegistry.register(DependencyModelBuilder.getInstance());
    }
}
