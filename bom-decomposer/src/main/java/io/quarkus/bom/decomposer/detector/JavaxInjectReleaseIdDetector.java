package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import org.eclipse.aether.artifact.Artifact;

public class JavaxInjectReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("javax.inject")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.origin().toString().equals("https://github.com/javax-inject/javax-inject")) {
                return releaseId;
            }
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("https://github.com/javax-inject/javax-inject"),
                    releaseId.version());
        }
        return null;
    }

}
