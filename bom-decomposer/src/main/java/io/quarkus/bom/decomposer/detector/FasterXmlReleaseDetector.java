package io.quarkus.bom.decomposer.detector;

import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class FasterXmlReleaseDetector implements ReleaseIdDetector {

	@Override
	public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException {
		if (!artifact.getGroupId().startsWith("com.fasterxml.jackson")) {
			return null;
		}
		return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("com.fasterxml.jackson"),
				ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
	}
}
