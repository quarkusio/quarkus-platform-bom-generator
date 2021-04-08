package io.quarkus.bom.resolver;

import org.eclipse.aether.artifact.Artifact;

public class ArtifactNotFoundException extends RuntimeException {

    public ArtifactNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArtifactNotFoundException(String message) {
        super(message);
    }

    public ArtifactNotFoundException(Artifact a) {
        super("Failed to resolve artifact " + a);
    }

    public ArtifactNotFoundException(Artifact a, Throwable t) {
        super("Failed to resolve artifact " + a, t);
    }
}
