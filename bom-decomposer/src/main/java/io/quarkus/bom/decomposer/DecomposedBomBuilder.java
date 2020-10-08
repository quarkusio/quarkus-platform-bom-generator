package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import org.eclipse.aether.artifact.Artifact;

public interface DecomposedBomBuilder {

    void bomSource(PomResolver bomSource);

    void bomArtifact(Artifact bomArtifact);

    void bomDependency(ReleaseId releaseId, Artifact artifact) throws BomDecomposerException;

    DecomposedBom build() throws BomDecomposerException;
}
