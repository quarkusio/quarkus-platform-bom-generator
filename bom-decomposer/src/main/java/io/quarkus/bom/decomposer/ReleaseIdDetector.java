package io.quarkus.bom.decomposer;

import org.eclipse.aether.artifact.Artifact;

public interface ReleaseIdDetector {

	ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException;
}
