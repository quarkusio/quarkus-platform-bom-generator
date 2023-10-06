package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class ApacheHttpComponentsReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (!artifact.getGroupId().equals("org.apache.httpcomponents")) {
            return null;
        }
        var releaseId = releaseResolver.readRevisionFromPom(artifact);
        if (!releaseId.getRepository().hasUrl()) {
            return null;
        }
        String origin = releaseId.getRepository().getUrl();
        int i = origin.lastIndexOf('/');
        if (i < 0) {
            return null;
        }
        String repoName = origin.substring(i + 1);
        String version = artifact.getVersion();
        if (repoName.equals("httpcomponents-parent") && version.equals("11")) {
            version += "-RC1";
        } else {
            version = "rel/v" + version;
        }
        return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/apache/" + repoName), version);
    }
}
