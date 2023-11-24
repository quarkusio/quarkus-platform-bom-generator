package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class GuavaReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.google.guava")) {
            if (artifact.getArtifactId().equals("failureaccess")) {
                // THIS DOESN'T WORK, the tag is off
                return ReleaseIdFactory.forScmAndTag("https://github.com/google/guava", artifact.getVersion());
            }
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String version = releaseId.getValue();
            if (version.endsWith("-jre")) {
                version = version.substring(0, version.length() - "-jre".length());
            } else if (version.endsWith("-android")) {
                version = version.substring(0, version.length() - "-android".length());
            }
            return ScmRevision.tag(releaseId.getRepository(), "v" + version);
        }
        return null;
    }
}
