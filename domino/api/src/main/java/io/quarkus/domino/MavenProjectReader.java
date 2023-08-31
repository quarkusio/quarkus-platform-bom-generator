package io.quarkus.domino;

import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalWorkspace;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;

public class MavenProjectReader {

    private static final Map<String, String> PACKAGING_TYPE = Map.of(
            "maven-archetype", ArtifactCoords.TYPE_JAR,
            "bundle", ArtifactCoords.TYPE_JAR,
            "maven-plugin", ArtifactCoords.TYPE_JAR,
            "war", ArtifactCoords.TYPE_JAR);

    private static String getTypeForPackaging(String packaging) {
        return PACKAGING_TYPE.getOrDefault(packaging, packaging);
    }

    public static List<ArtifactCoords> resolveModuleDependencies(LocalWorkspace ws) {
        Objects.requireNonNull(ws, "Workspace is null");
        final List<ArtifactCoords> result = new ArrayList<>(ws.getProjects().size());
        for (LocalProject project : ws.getProjects().values()) {
            if (isPublished(project)) {
                var type = getTypeForPackaging(project.getRawModel().getPackaging());
                result.add(ArtifactCoords.of(project.getGroupId(), project.getArtifactId(),
                        ArtifactCoords.DEFAULT_CLASSIFIER, type, project.getVersion()));
            }
        }
        return result;
    }

    private static boolean isPublished(LocalProject project) {
        final Model model = project.getModelBuildingResult() == null ? project.getRawModel()
                : project.getModelBuildingResult().getEffectiveModel();
        var modelProps = model.getProperties();
        if (Boolean.parseBoolean(modelProps.getProperty("maven.install.skip"))
                || Boolean.parseBoolean(modelProps.getProperty("maven.deploy.skip"))
                || Boolean.parseBoolean(modelProps.getProperty("skipNexusStagingDeployMojo"))) {
            return false;
        }
        if (model.getBuild() != null) {
            for (Plugin plugin : model.getBuild().getPlugins()) {
                if (plugin.getArtifactId().equals("maven-install-plugin")
                        || plugin.getArtifactId().equals("maven-deploy-plugin")) {
                    for (PluginExecution e : plugin.getExecutions()) {
                        if (e.getId().startsWith("default-") && e.getPhase().equals("none")) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
