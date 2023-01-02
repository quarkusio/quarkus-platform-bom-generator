package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class BouncyCastleReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.bouncycastle")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.version().asString();
            if (version.startsWith("r") && version.contains("rv")) {
                return releaseId;
            }
            int i = version.indexOf('.');
            if (i < 0) {
                return releaseId;
            }
            version = "r" + version.substring(0, i) + "rv" + version.substring(i + 1);
            return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.tag(version));
        }
        return null;
    }

}
