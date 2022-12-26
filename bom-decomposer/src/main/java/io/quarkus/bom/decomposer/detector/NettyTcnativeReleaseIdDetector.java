package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class NettyTcnativeReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().startsWith("netty-tcnative-")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseVersion version = releaseId.version();
            if (!version.asString().startsWith("netty-tcnative-parent-")) {
                return ReleaseIdFactory.create(releaseId.origin(),
                        ReleaseVersion.Factory.tag("netty-tcnative-parent-" + artifact.getVersion()));
            }
            return releaseId;
        }
        return null;
    }

}
