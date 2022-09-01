package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class ApacheMavenReleaseIdDetector implements ReleaseIdDetector {

    private static final String GITBOX_APACHE_ORG_REPOS_ASF = "https://gitbox.apache.org/repos/asf/";

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().startsWith("org.apache.maven")
                || artifact.getArtifactId().equals("apache") && artifact.getGroupId().equals("org.apache")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String repoUrl = releaseId.origin().toString();
            if (!repoUrl.startsWith(GITBOX_APACHE_ORG_REPOS_ASF)) {
                return releaseId;
            }
            ReleaseOrigin origin = ReleaseOrigin.Factory
                    .scmConnection(repoUrl.replace(GITBOX_APACHE_ORG_REPOS_ASF, "https://github.com/apache/"));
            String repoName = repoUrl.substring(GITBOX_APACHE_ORG_REPOS_ASF.length());
            if ("maven-wagon".equals(repoName)) {
                repoName = "wagon";
            } else if ("maven-apache-parent".equals(repoName)) {
                repoName = "apache";
            }
            return ReleaseIdFactory.create(origin, ReleaseVersion.Factory.tag(repoName + "-" + artifact.getVersion()));
        }
        return null;
    }
}
