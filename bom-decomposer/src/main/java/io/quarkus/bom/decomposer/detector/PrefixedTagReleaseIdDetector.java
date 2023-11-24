package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import java.util.Collection;
import org.eclipse.aether.artifact.Artifact;

public class PrefixedTagReleaseIdDetector implements ReleaseIdDetector {

    private final String tagPrefix;
    private final Collection<String> groupIds;

    public PrefixedTagReleaseIdDetector(String tagPrefix, Collection<String> groupIds) {
        this.tagPrefix = tagPrefix;
        this.groupIds = groupIds;
    }

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!groupIds.contains(artifact.getGroupId())) {
            return null;
        }

        var releaseId = idResolver.readRevisionFromPom(artifact);
        final String version = releaseId.getValue();
        if (version.startsWith(tagPrefix)) {
            return releaseId;
        }
        return ScmRevision.tag(releaseId.getRepository(), tagPrefix + version);
    }
}
