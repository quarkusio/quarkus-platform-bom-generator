package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import org.eclipse.aether.artifact.Artifact;

public class JavaxInterceptorReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("javax.interceptor")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.origin().toString().equals("https://github.com/javaee/javax.interceptor")) {
                return releaseId;
            }
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("https://github.com/javaee/javax.interceptor"),
                    releaseId.version());
        }
        return null;
    }

}
