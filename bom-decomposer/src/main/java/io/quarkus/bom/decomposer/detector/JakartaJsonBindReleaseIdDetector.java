package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class JakartaJsonBindReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("jakarta.json.bind")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (artifact.getVersion().equals("3.0.0")) {
                return releaseId;
            }
            String suffix = "";
            if (artifact.getVersion().equals("2.0.0")) {
                suffix = "-RELEASE";
            }
            String prefix = "";
            if (artifact.getVersion().equals("1.0.2")) {
                prefix = "1.0-";
                suffix = "-RELEASE";
            }
            return ScmRevision.tag(releaseId.getRepository(), prefix + artifact.getVersion() + suffix);
        }
        return null;
    }

}
