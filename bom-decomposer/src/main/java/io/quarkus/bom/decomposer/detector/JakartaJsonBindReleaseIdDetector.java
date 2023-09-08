package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class JakartaJsonBindReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("jakarta.json.bind")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
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
