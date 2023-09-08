package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class Slf4jReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.slf4j")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.getValue();
            if (version.startsWith("v_")) {
                return releaseId;
            }
            return ScmRevision.tag(releaseId.getRepository(), "v_" + version);
        }
        return null;
    }

}
