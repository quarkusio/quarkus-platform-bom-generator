package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class AsmReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.ow2.asm")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.version().asString();
            if (version.startsWith("ASM_")) {
                return releaseId;
            }
            version = version.replace('.', '_').toUpperCase();
            return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.tag("ASM_" + version));
        }
        return null;
    }

}
