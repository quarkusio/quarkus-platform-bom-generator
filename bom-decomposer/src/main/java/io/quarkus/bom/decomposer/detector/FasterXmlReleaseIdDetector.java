package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class FasterXmlReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("com.fasterxml.jackson")) {
            return null;
        }
        ReleaseId releaseId = idResolver.defaultReleaseId(artifact);
        String repoUrl = releaseId.origin().toString();
        int i = repoUrl.lastIndexOf('/');
        if (i < 0) {
            return releaseId;
        }
        if (repoUrl.contains("jackson-module-scala")) {
            repoUrl = repoUrl.replace("api@", "");
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection(repoUrl),
                    ReleaseVersion.Factory.tag("v" + artifact.getVersion()));
        }
        String repoName = repoUrl.substring(i + 1);
        String tag = releaseId.version().asString();
        if (tag.startsWith(repoName)) {
            return releaseId;
        }
        return ReleaseIdFactory.create(releaseId.origin(),
                ReleaseVersion.Factory.version(repoName + "-" + tag));
    }
}
