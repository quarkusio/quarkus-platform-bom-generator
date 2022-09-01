package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JakartaJsonBindReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("jakarta.json.bind")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (artifact.getVersion().equals("3.0.0")) {
                return releaseId;
            }
            String suffix = "";
            if (artifact.getVersion().equals("2.0.0")) {
                suffix = "-RELEASE";
            }
            String prefix = "";
            if (artifact.getVersion().equals("1.0.2")) {
                prefix = "1.0-";
                suffix = "-RELEASE";
            }
            return ReleaseIdFactory.create(releaseId.origin(),
                    ReleaseVersion.Factory.tag(prefix + artifact.getVersion() + suffix));
        }
        return null;
    }

}
