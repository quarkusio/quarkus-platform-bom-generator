package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class XStreamReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.thoughtworks.xstream")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.getValue().startsWith("XSTREAM_")) {
                return releaseId;
            }
            return ScmRevision.tag(releaseId.getRepository(),
                    "XSTREAM_" + artifact.getVersion().replace('.', '_'));
        }
        return null;
    }
}
