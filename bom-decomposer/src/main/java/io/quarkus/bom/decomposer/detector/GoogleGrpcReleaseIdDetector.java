package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class GoogleGrpcReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.google.api.grpc")) {
            // these artifacts are released from https://github.com/googleapis/sdk-platform-java
            // along with other artifacts that are versioned differently
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.version().isTag()) {
                return releaseId;
            }
            var origin = releaseId.origin();
            if (origin.isUrl() && origin.toString().equals("https://github.com/googleapis/sdk-platform-java")) {
                return ReleaseIdFactory.create(ReleaseOrigin.Factory.ga("com.google.api.grpc", "google-common-protos-parent"),
                        ReleaseVersion.Factory.version(artifact.getVersion()));
            }
        }
        return null;
    }

}
