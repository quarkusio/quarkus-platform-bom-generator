package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class HapiFhirReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("ca.uhn.hapi.fhir")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseOrigin origin = releaseId.origin();
            if (!"https://github.com/hapifhir/hapi-fhir".equals(origin.toString())) {
                origin = ReleaseOrigin.Factory.scmConnection("https://github.com/hapifhir/hapi-fhir");
            }
            ReleaseVersion version = releaseId.version();
            if (!version.asString().startsWith("v")) {
                version = ReleaseVersion.Factory.tag("v" + version.asString());
            }
            return ReleaseIdFactory.create(origin, version);
        }
        return null;
    }

}
