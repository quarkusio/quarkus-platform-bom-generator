package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class ApacheAvroReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.apache.avro")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String version = releaseId.getValue();
            if (version.startsWith("release-")) {
                return releaseId;
            }
            return ScmRevision.tag(releaseId.getRepository(), "release-" + version);
        }
        return null;
    }

}
