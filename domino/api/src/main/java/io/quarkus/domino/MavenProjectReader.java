package io.quarkus.domino;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalWorkspace;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;

public class MavenProjectReader {

    public static List<ArtifactCoords> resolveModuleDependencies(MavenArtifactResolver resolver) {

        final LocalWorkspace ws = resolver.getMavenContext().getWorkspace();
        final List<Path> createdDirs = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                for (Path p : createdDirs) {
                    IoUtils.recursiveDelete(p);
                }
            }
        }));
        ws.getProjects().values().forEach(p -> ensureResolvable(p, createdDirs));
        final List<ArtifactCoords> result = new ArrayList<>();
        for (LocalProject project : ws.getProjects().values()) {
            if (isPublished(project)) {
                result.add(ArtifactCoords.of(project.getGroupId(), project.getArtifactId(),
                        ArtifactCoords.DEFAULT_CLASSIFIER, project.getRawModel().getPackaging(), project.getVersion()));
            }
        }
        return result;
    }

    private static boolean isPublished(LocalProject project) {
        final Model model = project.getModelBuildingResult() == null ? project.getRawModel()
                : project.getModelBuildingResult().getEffectiveModel();
        String skipStr = model.getProperties().getProperty("maven.install.skip");
        if (skipStr != null && Boolean.parseBoolean(skipStr)) {
            return false;
        }
        skipStr = model.getProperties().getProperty("maven.deploy.skip");
        if (skipStr != null && Boolean.parseBoolean(skipStr)) {
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

    private static void ensureResolvable(LocalProject project, List<Path> createdDirs) {
        if (!project.getRawModel().getPackaging().equals(ArtifactCoords.TYPE_POM)) {
            final Path classesDir = project.getClassesDir();
            if (!Files.exists(classesDir)) {
                Path topDirToCreate = classesDir;
                while (!Files.exists(topDirToCreate.getParent())) {
                    topDirToCreate = topDirToCreate.getParent();
                }
                try {
                    Files.createDirectories(classesDir);
                    createdDirs.add(topDirToCreate);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create " + classesDir, e);
                }
            }
        }
    }
}
