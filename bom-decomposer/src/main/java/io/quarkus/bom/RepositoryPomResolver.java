package io.quarkus.bom;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;

public class RepositoryPomResolver implements PomResolver {

    private final Artifact pomArtifact;
    private Model model;

    public RepositoryPomResolver(Artifact pomArtifact) {
        this.pomArtifact = pomArtifact;
    }

    @Override
    public Path pomPath() {
        if (isResolved()) {
            return pomArtifact.getFile().toPath();
        }
        throw new RuntimeException(pomArtifact + " has not been resolved");
    }

    @Override
    public String source() {
        return pomArtifact.toString();
    }

    @Override
    public Model model() throws IOException {
        return model == null ? model = ModelUtils.readModel(pomPath()) : model;
    }

    @Override
    public Model readLocalModel(Path pom) throws IOException {
        if (Files.isSameFile(pom, pomPath())) {
            return model();
        }
        throw new IllegalArgumentException("This implementation supports only " + pomPath() + ": " + pom);
    }

    @Override
    public Artifact pomArtifact() {
        return pomArtifact;
    }

    @Override
    public boolean isResolved() {
        return pomArtifact.getFile() != null;
    }
}
