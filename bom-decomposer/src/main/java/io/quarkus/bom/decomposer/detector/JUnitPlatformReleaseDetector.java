package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import org.eclipse.aether.artifact.Artifact;

public class JUnitPlatformReleaseDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("org.junit")) {
            return null;
        }
        if (artifact.getGroupId().startsWith("org.junit.platform")) {
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("org.junit.platform"),
                    ReleaseVersion.Factory.version(ModelUtils.getVersion(idResolver.model(artifact))));
        }
        return null;
    }
}
