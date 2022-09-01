package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JBossJaxRsApiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("jboss-jaxrs-api_2.1_spec")
                && artifact.getGroupId().equals("org.jboss.spec.javax.ws.rs")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            return ReleaseIdFactory.create(releaseId.origin(),
                    ReleaseVersion.Factory.version(artifact.getArtifactId() + "-" + releaseId.version().asString()));
        }
        return null;
    }
}
