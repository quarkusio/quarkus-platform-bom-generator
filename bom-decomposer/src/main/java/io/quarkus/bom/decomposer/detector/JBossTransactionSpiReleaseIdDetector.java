package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class JBossTransactionSpiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getArtifactId().equals("jboss-transaction-spi") && artifact.getGroupId().equals("org.jboss")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            String repoUrl = releaseId.getRepository().getId();
            if (!repoUrl.contains("${")) {
                return releaseId;
            }
            return ReleaseIdFactory.forScmAndTag("https://github.com/jbosstm/jboss-transaction-spi", artifact.getVersion());
        }
        return null;
    }

}
