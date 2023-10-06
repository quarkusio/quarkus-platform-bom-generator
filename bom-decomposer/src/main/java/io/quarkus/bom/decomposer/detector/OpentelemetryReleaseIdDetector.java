package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.bom.resolver.ArtifactNotFoundException;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class OpentelemetryReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("io.opentelemetry")) {
            return null;
        }

        ScmRevision releaseId = null;
        try {
            releaseId = idResolver.readRevisionFromPom(artifact);
        } catch (ArtifactNotFoundException e) {
            // prod may strip the -alpha qualifier
            if (artifact.getVersion().endsWith("-alpha")) {
                throw e;
            }
            releaseId = idResolver.readRevisionFromPom(artifact.setVersion(artifact.getVersion() + "-alpha"));
        }
        String version = releaseId.getValue();
        if (version.endsWith("-alpha")) {
            version = version.substring(0, version.length() - "-alpha".length());
        }
        if (version.charAt(0) == 'v') {
            return releaseId;
        }
        return ScmRevision.tag(releaseId.getRepository(), "v" + version);
    }
}
