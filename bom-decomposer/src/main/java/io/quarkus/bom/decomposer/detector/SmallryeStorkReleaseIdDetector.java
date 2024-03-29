package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class SmallryeStorkReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("io.smallrye.stork")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (releaseId.getRepository().getId().contains("smallrye-load-balancer")) {
                return ScmRevision.tag(
                        ScmRepository.ofUrl(
                                releaseId.getRepository().getId().replace("smallrye-load-balancer", "smallrye-stork")),
                        releaseId.getValue());
            }
            return releaseId;
        }
        return null;
    }

}
