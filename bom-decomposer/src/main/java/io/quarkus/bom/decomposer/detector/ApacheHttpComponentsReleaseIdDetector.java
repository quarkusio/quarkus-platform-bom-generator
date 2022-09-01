package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import org.eclipse.aether.artifact.Artifact;

public class ApacheHttpComponentsReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (!artifact.getGroupId().equals("org.apache.httpcomponents")) {
            return null;
        }
        ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
        String origin = releaseId.origin().toString();
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
        return ReleaseIdFactory.forScmAndTag("https://github.com/apache/" + repoName, version);
    }
}
