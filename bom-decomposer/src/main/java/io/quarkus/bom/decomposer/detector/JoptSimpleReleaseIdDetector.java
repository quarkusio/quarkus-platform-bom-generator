package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class JoptSimpleReleaseIdDetector implements ReleaseIdDetector {

    private static final String JOPT_SIMPLE = "jopt-simple-";

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("net.sf.jopt-simple")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.getValue();
            if (version.startsWith(JOPT_SIMPLE)) {
                return releaseId;
            }
            return ScmRevision.tag(releaseId.getRepository(), JOPT_SIMPLE + version);
        }
        return null;
    }

}
