package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JavaxElReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("javax.el")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (releaseId.origin().toString().equals("https://github.com/javaee/el-spec")) {
                return releaseId;
            }
            return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("https://github.com/javaee/el-spec"),
                    ReleaseVersion.Factory.tag(artifact.getArtifactId() + "-" + artifact.getVersion()));
        }
        return null;
    }

}
