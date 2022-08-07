package io.quarkus.bom.decomposer;

import org.eclipse.aether.artifact.Artifact;

public interface ReleaseIdDetector {

    ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact) throws BomDecomposerException;
}
