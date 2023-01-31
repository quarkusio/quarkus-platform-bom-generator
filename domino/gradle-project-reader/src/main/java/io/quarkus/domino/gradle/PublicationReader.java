package io.quarkus.domino.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradlePublication;
import org.gradle.tooling.model.gradle.ProjectPublications;

public class PublicationReader implements BuildAction<List<GradlePublication>> {

    @Override
    public List<GradlePublication> execute(BuildController controller) {
        GradleProject project = controller.getModel(GradleProject.class);
        List<GradlePublication> publications = new ArrayList<>();
        collectPublications(project, controller, publications);
        return publications;
    }

    private void collectPublications(GradleProject project, BuildController controller,
            List<GradlePublication> publications) {
        ProjectPublications pp = controller.getModel(project, ProjectPublications.class);
        for (GradlePublication pub : pp.getPublications()) {
            publications.add(pub);
        }
        for (GradleProject child : project.getChildren()) {
            collectPublications(child, controller, publications);
        }
    }
}
