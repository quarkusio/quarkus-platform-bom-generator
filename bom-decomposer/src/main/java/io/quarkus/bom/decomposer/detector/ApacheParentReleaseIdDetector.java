package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class ApacheParentReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.apache")
                && artifact.getArtifactId().equals("apache")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.origin().toString().equals("https://github.com/apache/maven-apache-parent")) {
                return releaseId;
            }
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("https://github.com/apache/maven-apache-parent"),
                    ReleaseVersion.Factory.tag("apache-" + artifact.getVersion()));
        }
        return null;
    }

}
