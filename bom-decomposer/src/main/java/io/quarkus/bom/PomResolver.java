package io.quarkus.bom;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;

public interface PomResolver {
    Artifact pomArtifact();

    boolean isResolved();

    Path pomPath();

    String source();

    Model model() throws IOException;

    Model readLocalModel(Path pom) throws IOException;
}
