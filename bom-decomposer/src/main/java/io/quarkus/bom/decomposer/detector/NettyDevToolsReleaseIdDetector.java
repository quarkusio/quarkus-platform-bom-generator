package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class NettyDevToolsReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("netty-dev-tools") && artifact.getGroupId().equals("io.netty")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseOrigin origin = releaseId.origin();
            if (!origin.toString().equals("https://github.com/netty/netty")) {
                origin = ReleaseOrigin.Factory.scmConnection("https://github.com/netty/netty");
            }
            ReleaseVersion version = releaseId.version();
            if (!version.asString().startsWith("netty-")) {
                version = ReleaseVersion.Factory.tag("netty-" + artifact.getVersion());
            }
            return ReleaseIdFactory.create(origin, version);
        }
        return null;
    }

}
