package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
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
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!groupIds.contains(artifact.getGroupId())) {
            return null;
        }

        final ReleaseId releaseId = idResolver.defaultReleaseId(artifact);
        final String version = releaseId.version().asString();
        if (version.startsWith(tagPrefix)) {
            return releaseId;
        }
        return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.version(tagPrefix + version));
    }
}
