package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class JakartaInterceptorReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("jakarta.interceptor")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (releaseId.getRepository().hasUrl()
                    && !releaseId.getRepository().getUrl().startsWith("https://github.com/eclipse-ee4j/interceptor-api")) {
                return releaseId;
            }
            return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/jakartaee/interceptors"),
                    artifact.getVersion() + "-RELEASE");
        }
        return null;
    }

}
