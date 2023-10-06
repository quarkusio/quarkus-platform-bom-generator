package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class FasterXmlReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("com.fasterxml.jackson")) {
            return null;
        }
        var releaseId = idResolver.readRevisionFromPom(artifact);
        if (!releaseId.getRepository().hasUrl()) {
            return releaseId;
        }
        String repoUrl = releaseId.getRepository().getUrl();
        int i = repoUrl.lastIndexOf('/');
        if (i < 0) {
            return releaseId;
        }
        if (repoUrl.contains("jackson-module-scala")) {
            repoUrl = repoUrl.replace("api@", "");
            return ScmRevision.tag(ScmRepository.ofUrl(repoUrl), "v" + artifact.getVersion());
        }
        String repoName = repoUrl.substring(i + 1);
        String tag = releaseId.getValue();
        if (tag.startsWith(repoName)) {
            return releaseId;
        }
        return ScmRevision.tag(releaseId.getRepository(), repoName + "-" + tag);
    }
}
