package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class ResteasyBomReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        return artifact.getArtifactId().equals("resteasy-bom") && artifact.getGroupId().equals("org.jboss.resteasy")
                ? ReleaseIdFactory.forScmAndTag("https://github.com/resteasy/resteasy", artifact.getVersion())
                : null;
    }
}
