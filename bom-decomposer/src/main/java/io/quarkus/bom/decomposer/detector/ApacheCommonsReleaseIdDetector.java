package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class ApacheCommonsReleaseIdDetector implements ReleaseIdDetector {

    private static final String GITBOX_APACHE_ORG_REPOS_ASF = "https://gitbox.apache.org/repos/asf/";

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        final String artifactId = artifact.getArtifactId();
        if (artifact.getGroupId().equals("org.apache.commons")
                && (artifact.getArtifactId().equals("commons-lang3")
                        || artifact.getArtifactId().equals("commons-text")
                        || artifactId.equals("commons-compress"))) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String repoUrl = releaseId.getRepository().toString();
            if (releaseId.getRepository().hasUrl()
                    && !releaseId.getRepository().getUrl().startsWith(GITBOX_APACHE_ORG_REPOS_ASF)) {
                return releaseId;
            }
            var origin = ScmRepository.ofUrl(repoUrl.replace(GITBOX_APACHE_ORG_REPOS_ASF, "https://github.com/apache/"));
            String repoName = artifactId.equals("commons-compress") ? ""
                    : repoUrl.substring(GITBOX_APACHE_ORG_REPOS_ASF.length()) + "-";
            return ScmRevision.tag(origin, "rel/" + repoName + artifact.getVersion());
        }
        return null;
    }
}
