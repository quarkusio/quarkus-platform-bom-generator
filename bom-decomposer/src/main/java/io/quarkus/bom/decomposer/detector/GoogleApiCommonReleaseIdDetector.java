package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class GoogleApiCommonReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.google.api")
                && artifact.getArtifactId().equals("api-common")) {
            // these artifacts are released from https://github.com/googleapis/sdk-platform-java
            // along with other artifacts that are versioned differently
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (releaseId.isTag()) {
                return releaseId;
            }
            var origin = releaseId.getRepository();
            if (origin.hasUrl() && origin.getUrl().equals("https://github.com/googleapis/sdk-platform-java")) {
                return ScmRevision.version(ScmRepository.ofId("com.google.api:api-common"), artifact.getVersion());
            }
        }
        return null;
    }

}
