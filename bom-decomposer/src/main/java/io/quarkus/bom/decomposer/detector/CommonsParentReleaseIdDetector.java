package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class CommonsParentReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.apache.commons")
                && artifact.getArtifactId().equals("commons-parent")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.getRepository().hasUrl()
                    && releaseId.getRepository().getUrl().equals("https://github.com/apache/commons-parent")) {
                return releaseId;
            }
            return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/apache/commons-parent"),
                    "commons-parent-" + releaseId.getValue());
        }
        return null;
    }

}
