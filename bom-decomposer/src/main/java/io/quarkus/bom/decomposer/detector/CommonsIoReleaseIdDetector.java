package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class CommonsIoReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("commons-io")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            var origin = releaseId.getRepository();
            if (!origin.hasUrl() || !origin.getUrl().equals("https://github.com/apache/commons-io")) {
                origin = ScmRepository.ofUrl("https://github.com/apache/commons-io");
            }
            String[] arr = artifact.getVersion().split("\\.");
            if ("2".equals(arr[0]) && Integer.parseInt(arr[1]) < 7) {
                return ScmRevision.tag(origin, "commons-io-" + artifact.getVersion());
            }
            return ScmRevision.tag(origin, "rel/commons-io-" + artifact.getVersion());
        }
        return null;
    }

}
