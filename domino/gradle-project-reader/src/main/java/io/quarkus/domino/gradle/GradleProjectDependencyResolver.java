package io.quarkus.domino.gradle;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

public class GradleProjectDependencyResolver implements BuildAction<GradleProjectDependencies> {

    @Override
    public GradleProjectDependencies execute(BuildController controller) {
        final List<GradlePublishedModule> modules = controller.run(Collections.singletonList(new PublicationReader())).get(0)
                .stream()
                .map(p -> GradlePublishedModule.of(p.getId().getGroup(), p.getId().getName(), p.getId().getVersion(),
                        p.getProjectIdentifier().getProjectPath()))
                .collect(Collectors.toList());
        return controller.getModel(GradleProjectDependencies.class, GradleProjectDependencyParameters.class,
                new Action<GradleProjectDependencyParameters>() {
                    @Override
                    public void execute(GradleProjectDependencyParameters t) {
                        t.setModules(modules);
                    }
                });
    }
}
