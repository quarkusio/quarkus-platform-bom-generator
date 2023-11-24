package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class NettyDevToolsReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("netty-dev-tools") && artifact.getGroupId().equals("io.netty")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            var origin = releaseId.getRepository();
            if (!origin.hasUrl() || !origin.getUrl().equals("https://github.com/netty/netty")) {
                origin = ScmRepository.ofUrl("https://github.com/netty/netty");
            }
            String version = releaseId.getValue();
            if (!version.startsWith("netty-")) {
                version = "netty-" + artifact.getVersion();
            }
            return ScmRevision.tag(origin, version);
        }
        return null;
    }

}
