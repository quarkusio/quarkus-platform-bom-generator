package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import org.eclipse.aether.artifact.Artifact;

public class JakartaPersistenceReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("jakarta.persistence")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            if (!releaseId.origin().toString().contains("eclipse-ee4j")) {
                return releaseId;
            }
            int i = artifact.getVersion().indexOf('.');
            if (i > 0) {
                i = artifact.getVersion().indexOf('.', i + 1);
                if (i < 0) {
                    return releaseId;
                }
            }
            return ReleaseIdFactory.forScmAndTag("https://github.com/jakartaee/persistence",
                    artifact.getVersion().substring(0, i) + "-" + artifact.getVersion() + "-RELEASE");
        }
        return null;
    }

}
