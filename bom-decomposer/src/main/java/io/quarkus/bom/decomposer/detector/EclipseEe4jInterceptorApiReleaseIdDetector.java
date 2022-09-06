package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class EclipseEe4jInterceptorApiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.eclipse.ee4j.interceptor-api")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (!releaseId.origin().toString().equals("https://github.com/eclipse-ee4j/interceptor-api")) {
                return releaseId;
            }
            return ReleaseIdFactory.create(
                    ReleaseOrigin.Factory.scmConnection("https://github.com/jakartaee/interceptors/tags"),
                    ReleaseVersion.Factory.tag(artifact.getVersion() + "-RELEASE"));
        }
        return null;
    }

}
