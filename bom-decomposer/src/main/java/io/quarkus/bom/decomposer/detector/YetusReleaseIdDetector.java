package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class YetusReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.apache.yetus")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String version = releaseId.getValue();
            if (version.startsWith("rel/")) {
                return releaseId;
            }
            return ScmRevision.tag(releaseId.getRepository(), "rel/" + version);
        }
        return null;
    }

}
