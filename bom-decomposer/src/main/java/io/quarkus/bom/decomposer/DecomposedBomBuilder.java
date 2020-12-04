package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public interface DecomposedBomBuilder {

    void bomSource(PomResolver bomSource);

    void bomArtifact(Artifact bomArtifact);

    void bomDependency(ReleaseId releaseId, Dependency dep) throws BomDecomposerException;

    DecomposedBom build() throws BomDecomposerException;
}
