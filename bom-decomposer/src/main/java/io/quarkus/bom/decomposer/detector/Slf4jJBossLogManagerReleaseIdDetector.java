package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import org.eclipse.aether.artifact.Artifact;

public class Slf4jJBossLogManagerReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("slf4j-jboss-logmanager")
                && artifact.getGroupId().equals("org.jboss.slf4j")) {
            return ReleaseIdFactory.forScmAndTag("https://github.com/jboss-logging/slf4j-jboss-logmanager",
                    artifact.getVersion());
        }
        return null;
    }

}
