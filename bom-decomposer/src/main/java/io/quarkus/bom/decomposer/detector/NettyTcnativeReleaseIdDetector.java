package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class NettyTcnativeReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().startsWith("netty-tcnative-")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.getValue();
            if (!version.startsWith("netty-tcnative-parent-")) {
                return ScmRevision.tag(releaseId.getRepository(),
                        "netty-tcnative-parent-" + artifact.getVersion());
            }
            return releaseId;
        }
        return null;
    }

}
