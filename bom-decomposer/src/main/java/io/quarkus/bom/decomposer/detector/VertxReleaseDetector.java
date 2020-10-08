package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import org.eclipse.aether.artifact.Artifact;

public class VertxReleaseDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("io.vertx")) {
            return null;
        }
        if (artifact.getArtifactId().equals("vertx-docgen")) {
            return null;
        }
        return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("io.vertx"),
                ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
    }
}
