package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class GlassfishJsonpReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.glassfish")
                && artifact.getArtifactId().contains("json")) {
            final ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.version().asString();
            if (version.endsWith("-RELEASE")) {
                return releaseId;
            }
            if (artifact.getVersion().equals("1.1.6")) {
                version = "1.1-" + version;
            }
            ReleaseOrigin origin = releaseId.origin();
            return ReleaseIdFactory.create(origin, ReleaseVersion.Factory.tag(version + "-RELEASE"));
        }
        return null;
    }

}
