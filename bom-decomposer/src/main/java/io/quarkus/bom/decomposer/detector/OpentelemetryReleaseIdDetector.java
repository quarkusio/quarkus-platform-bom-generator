package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class OpentelemetryReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("io.opentelemetry")) {
            return null;
        }

        final ReleaseId releaseId = idResolver.defaultReleaseId(artifact);
        String version = releaseId.version().asString();
        if (version.endsWith("-alpha")) {
            version = version.substring(0, version.length() - "-alpha".length());
        }
        if (version.charAt(0) == 'v') {
            return releaseId;
        }
        return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.version("v" + version));
    }
}
