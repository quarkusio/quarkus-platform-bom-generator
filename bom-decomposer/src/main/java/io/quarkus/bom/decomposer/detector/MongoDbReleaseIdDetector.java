package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class MongoDbReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.mongodb")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.getValue();
            if (version.startsWith("r")) {
                return releaseId;
            }
            return ScmRevision.tag(releaseId.getRepository(), "r" + version);
        }
        return null;
    }
}
