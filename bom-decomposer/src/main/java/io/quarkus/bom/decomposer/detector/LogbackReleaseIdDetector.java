package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class LogbackReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("ch.qos.logback")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseOrigin origin = releaseId.origin();
            if (!origin.toString().equals("https://github.com/qos-ch/logback")) {
                origin = ReleaseOrigin.Factory.scmConnection("https://github.com/qos-ch/logback");
            }
            ReleaseVersion version = releaseId.version();
            if (!version.asString().startsWith("v_")) {
                version = ReleaseVersion.Factory.tag("v_" + artifact.getVersion());
            }
            return ReleaseIdFactory.create(origin, version);
        }
        return null;
    }

}
