package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class LogbackReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("ch.qos.logback")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            var origin = releaseId.getRepository();
            if (!origin.hasUrl() || !origin.getUrl().equals("https://github.com/qos-ch/logback")) {
                origin = ScmRepository.ofUrl("https://github.com/qos-ch/logback");
            }
            String version = releaseId.getValue();
            if (!version.startsWith("v_")) {
                version = "v_" + artifact.getVersion();
            }
            return ScmRevision.tag(origin, version);
        }
        return null;
    }

}
