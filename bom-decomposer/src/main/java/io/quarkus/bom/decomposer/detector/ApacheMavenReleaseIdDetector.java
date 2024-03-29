package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class ApacheMavenReleaseIdDetector implements ReleaseIdDetector {

    private static final String GITBOX_APACHE_ORG_REPOS_ASF = "https://gitbox.apache.org/repos/asf/";

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().startsWith("org.apache.maven")
                || artifact.getArtifactId().equals("apache") && artifact.getGroupId().equals("org.apache")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (releaseId.getRepository().hasUrl()
                    && !releaseId.getRepository().getUrl().startsWith(GITBOX_APACHE_ORG_REPOS_ASF)) {
                return releaseId;
            }
            String repoUrl = releaseId.getRepository().getId();
            var origin = ScmRepository.ofUrl(repoUrl.replace(GITBOX_APACHE_ORG_REPOS_ASF, "https://github.com/apache/"));
            String repoName = repoUrl.substring(GITBOX_APACHE_ORG_REPOS_ASF.length());
            if ("maven-wagon".equals(repoName)) {
                repoName = "wagon";
            } else if ("maven-apache-parent".equals(repoName)) {
                repoName = "apache";
            }
            return ScmRevision.tag(origin, repoName + "-" + artifact.getVersion());
        }
        return null;
    }
}
