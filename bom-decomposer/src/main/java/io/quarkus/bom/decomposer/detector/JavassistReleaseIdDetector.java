package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class JavassistReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.javassist")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.getValue().toLowerCase();
            if (version.startsWith("rel_")) {
                return releaseId;
            }
            version = version.replace('.', '_');
            version = version.replace('-', '_');
            return ScmRevision.tag(releaseId.getRepository(), "rel_" + version);
        }
        return null;
    }

}
