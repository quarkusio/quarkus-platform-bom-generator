package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class CommonsIoReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("commons-io")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseOrigin origin = releaseId.origin();
            if (!origin.toString().equals("https://github.com/apache/commons-io")) {
                origin = ReleaseOrigin.Factory.scmConnection("https://github.com/apache/commons-io");
            }
            String[] arr = artifact.getVersion().split("\\.");
            if ("2".equals(arr[0]) && Integer.parseInt(arr[1]) < 7) {
                return ReleaseIdFactory.create(origin, ReleaseVersion.Factory.tag("commons-io-" + artifact.getVersion()));
            }
            return ReleaseIdFactory.create(origin, ReleaseVersion.Factory.tag("rel/commons-io-" + artifact.getVersion()));
        }
        return null;
    }

}
