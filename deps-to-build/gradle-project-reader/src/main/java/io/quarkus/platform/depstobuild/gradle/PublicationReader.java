package io.quarkus.platform.depstobuild.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradlePublication;
import org.gradle.tooling.model.gradle.ProjectPublications;

public class PublicationReader implements BuildAction<List<GradleModuleVersion>> {

    @Override
    public List<GradleModuleVersion> execute(BuildController controller) {
        GradleProject project = controller.getModel(GradleProject.class);
        List<GradleModuleVersion> publications = new ArrayList<>();
        getPublications(project, controller, publications);
        return publications;
    }

    private void getPublications(GradleProject project, BuildController controller,
            List<GradleModuleVersion> publications) {
        ProjectPublications pp = controller.getModel(project, ProjectPublications.class);
        for (GradlePublication pub : pp.getPublications()) {
            publications.add(pub.getId());
        }
        for (GradleProject child : project.getChildren()) {
            getPublications(child, controller, publications);
        }
    }
}
