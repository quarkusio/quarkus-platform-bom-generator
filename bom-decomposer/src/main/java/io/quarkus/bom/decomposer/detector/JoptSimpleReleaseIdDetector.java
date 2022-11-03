package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JoptSimpleReleaseIdDetector implements ReleaseIdDetector {

    private static final String JOPT_SIMPLE = "jopt-simple-";

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("net.sf.jopt-simple")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.version().asString();
            if (version.startsWith(JOPT_SIMPLE)) {
                return releaseId;
            }
            return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.tag(JOPT_SIMPLE + version));
        }
        return null;
    }

}
