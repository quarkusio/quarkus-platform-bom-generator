package io.quarkus.bom;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class FsPomResolver implements PomResolver {

    private final Path projectPom;
    private Artifact artifact;
    private Model model;

    public FsPomResolver(Path projectPom) {
        if (!Files.exists(projectPom)) {
            throw new IllegalArgumentException("Path does not exist " + projectPom);
        }
        this.projectPom = projectPom;
    }

    @Override
    public Path pomPath() {
        return projectPom;
    }

    @Override
    public Model readLocalModel(Path pom) throws IOException {
        if (pom.equals(projectPom)) {
            return model();
        }
        if (Files.isDirectory(pom)) {
            pom = pom.resolve("pom.xml");
        }
        return Files.exists(pom) ? ModelUtils.readModel(pom) : null;
    }

    @Override
    public Model model() throws IOException {
        return model == null ? model = ModelUtils.readModel(projectPom) : model;
    }

    @Override
    public String source() {
        return projectPom.toString();
    }

    @Override
    public Artifact pomArtifact() {
        if (artifact == null) {
            try {
                final Model model = model();
                artifact = new DefaultArtifact(ModelUtils.getGroupId(model), model.getArtifactId(), null, "pom",
                        ModelUtils.getVersion(model));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read model of " + projectPom, e);
            }
        }
        return artifact;
    }

    @Override
    public boolean isResolved() {
        return true;
    }
}
