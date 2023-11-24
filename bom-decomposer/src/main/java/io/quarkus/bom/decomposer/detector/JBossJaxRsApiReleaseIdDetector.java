package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class JBossJaxRsApiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("jboss-jaxrs-api_2.1_spec")
                && artifact.getGroupId().equals("org.jboss.spec.javax.ws.rs")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            return ScmRevision.tag(releaseId.getRepository(),
                    artifact.getArtifactId() + "-" + releaseId.getValue());
        }
        return null;
    }
}
