package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class HapiFhirReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("ca.uhn.hapi.fhir")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            var origin = releaseId.getRepository();
            if (!origin.hasUrl() || !"https://github.com/hapifhir/hapi-fhir".equals(origin.getUrl())) {
                origin = ScmRepository.ofUrl("https://github.com/hapifhir/hapi-fhir");
            }
            var version = releaseId.getValue();
            if (!version.startsWith("v")) {
                version += "v";
            }
            return ScmRevision.tag(origin, version);
        }
        return null;
    }

}
