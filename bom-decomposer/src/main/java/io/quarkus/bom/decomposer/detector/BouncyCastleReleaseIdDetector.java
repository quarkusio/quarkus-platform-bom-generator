package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class BouncyCastleReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.bouncycastle")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String version = releaseId.getValue();
            if (version.startsWith("r") && version.contains("rv")) {
                return releaseId;
            }
            int i = version.indexOf('.');
            if (i < 0) {
                return releaseId;
            }
            version = "r" + version.substring(0, i) + "rv" + version.substring(i + 1);
            return ScmRevision.tag(releaseId.getRepository(), version);
        }
        return null;
    }

}
