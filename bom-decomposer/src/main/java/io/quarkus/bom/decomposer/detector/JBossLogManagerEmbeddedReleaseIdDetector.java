package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import org.eclipse.aether.artifact.Artifact;

public class JBossLogManagerEmbeddedReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("jboss-logmanager-embedded")
                && artifact.getGroupId().equals("org.jboss.logmanager")) {
            return ReleaseIdFactory.forScmAndTag("https://github.com/dmlloyd/jboss-logmanager-embedded", artifact.getVersion());
        }
        return null;
    }

}
