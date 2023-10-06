package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class AnimalSnifferReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.codehaus.mojo")
                && artifact.getArtifactId().startsWith("animal-sniffer")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (releaseId.getRepository().hasUrl()
                    && releaseId.getRepository().getUrl().equals("https://github.com/mojohaus/animal-sniffer")) {
                return releaseId;
            }
            return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/mojohaus/animal-sniffer"),
                    "animal-sniffer-parent-" + releaseId.getValue());
        }
        return null;
    }

}
