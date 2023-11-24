package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class GlassfishJsonpReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.glassfish")
                && artifact.getArtifactId().contains("json")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String version = releaseId.getValue();
            if (version.endsWith("-RELEASE")) {
                return releaseId;
            }
            if (artifact.getVersion().equals("1.1.6")) {
                version = "1.1-" + version;
            }
            var origin = releaseId.getRepository();
            return ScmRevision.tag(origin, version + "-RELEASE");
        }
        return null;
    }

}
