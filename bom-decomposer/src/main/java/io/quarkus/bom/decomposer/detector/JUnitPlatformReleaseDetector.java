package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JUnitPlatformReleaseDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (artifact.getGroupId().startsWith("org.junit") && artifact.getVersion().startsWith("5.")) {
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("https://github.com/junit-team/junit5"),
                    ReleaseVersion.Factory.version("r" + artifact.getVersion()));
        }
        return null;
    }
}
