package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class EclipseEe4jInterceptorApiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.eclipse.ee4j.interceptor-api")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            if (!releaseId.getRepository().hasUrl()
                    || !releaseId.getRepository().getUrl().equals("https://github.com/eclipse-ee4j/interceptor-api")) {
                return releaseId;
            }
            return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/jakartaee/interceptors/tags"),
                    artifact.getVersion() + "-RELEASE");
        }
        return null;
    }

}
