package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import org.eclipse.aether.artifact.Artifact;

public class JBossTransactionSpiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("jboss-transaction-spi") && artifact.getGroupId().equals("org.jboss")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String repoUrl = releaseId.origin().toString();
            if (!repoUrl.contains("${")) {
                return releaseId;
            }
            return ReleaseIdFactory.forScmAndTag("https://github.com/jbosstm/jboss-transaction-spi", artifact.getVersion());
        }
        return null;
    }

}
