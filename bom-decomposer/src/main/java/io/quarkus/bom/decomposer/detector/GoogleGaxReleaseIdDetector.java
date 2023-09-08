package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class GoogleGaxReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.google.api")
                && artifact.getArtifactId().startsWith("gax")) {
            // these artifacts are released from https://github.com/googleapis/sdk-platform-java
            // along with other artifacts that are versioned differently
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.isTag()) {
                return releaseId;
            }
            var origin = releaseId.getRepository();
            if (origin.hasUrl() && origin.getUrl().equals("https://github.com/googleapis/sdk-platform-java")) {
                return ScmRevision.version(ScmRepository.ofId("com.google.api:gax-parent"), artifact.getVersion());
            }
        }
        return null;
    }

}
