package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class GlassfishJerseyReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().startsWith("org.glassfish.jersey")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            var origin = releaseId.getRepository();
            if (origin.hasUrl() && origin.getUrl().equals("https://github.com/eclipse-ee4j/jersey")) {
                return releaseId;
            }
            return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/eclipse-ee4j/jersey"), releaseId.getValue());
        }
        return null;
    }

}
