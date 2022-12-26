package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class ApacheCommonsReleaseIdDetector implements ReleaseIdDetector {

    private static final String GITBOX_APACHE_ORG_REPOS_ASF = "https://gitbox.apache.org/repos/asf/";

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        final String artifactId = artifact.getArtifactId();
        if (artifact.getGroupId().equals("org.apache.commons")
                && (artifact.getArtifactId().equals("commons-parent")
                        || artifact.getArtifactId().equals("commons-lang3")
                        || artifact.getArtifactId().equals("commons-text")
                        || artifactId.equals("commons-compress"))
                || artifact.getGroupId().equals("commons-io")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String repoUrl = releaseId.origin().toString();
            if (!repoUrl.startsWith(GITBOX_APACHE_ORG_REPOS_ASF)) {
                return releaseId;
            }
            ReleaseOrigin origin = ReleaseOrigin.Factory
                    .scmConnection(repoUrl.replace(GITBOX_APACHE_ORG_REPOS_ASF, "https://github.com/apache/"));
            String repoName = artifactId.equals("commons-compress") ? ""
                    : repoUrl.substring(GITBOX_APACHE_ORG_REPOS_ASF.length()) + "-";
            return ReleaseIdFactory.create(origin, ReleaseVersion.Factory.tag("rel/" + repoName + artifact.getVersion()));
        }
        return null;
    }
}
