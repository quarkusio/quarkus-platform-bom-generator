package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class HibernateReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("org.hibernate")) {
            return null;
        }
        final ReleaseId releaseId = idResolver.defaultReleaseId(artifact);
        String version = releaseId.version().asString();
        if (!version.endsWith(".Final")) {
            return releaseId;
        }
        return ReleaseIdFactory.create(releaseId.origin(),
                ReleaseVersion.Factory.version(version.substring(0, version.length() - ".Final".length())));
    }
}
