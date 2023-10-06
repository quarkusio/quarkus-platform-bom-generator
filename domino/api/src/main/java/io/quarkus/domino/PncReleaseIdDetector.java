package io.quarkus.domino;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.manifest.PncArtifactBuildInfo;
import io.quarkus.domino.manifest.PncArtifactBuildInfo.Build;
import io.quarkus.domino.manifest.PncArtifactBuildInfo.Content;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class PncReleaseIdDetector implements ReleaseIdDetector {

    private final PncBuildInfoProvider pncInfoProvider;

    public PncReleaseIdDetector(PncBuildInfoProvider pncInfoProvider) {
        this.pncInfoProvider = pncInfoProvider;
    }

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        var pncInfo = pncInfoProvider
                .getBuildInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        if (pncInfo == null) {
            return null;
        }
        var build = getBuild(pncInfo);
        if (build == null) {
            return null;
        }
        var repoUrl = getRepoUrl(build);
        var tag = getTag(build);
        return repoUrl == null || tag == null ? null : ScmRevision.tag(ScmRepository.ofUrl(repoUrl), tag);
    }

    private static String getRepoUrl(Build build) {
        return build == null ? null : build.getScmUrl();
    }

    private static String getTag(Build build) {
        return build == null ? null : build.getScmTag();
    }

    private static Build getBuild(PncArtifactBuildInfo pncInfo) {
        if (pncInfo == null) {
            return null;
        }
        final Content content = PncArtifactBuildInfo.getContent(pncInfo);
        if (content == null) {
            return null;
        }
        return content.getBuild();
    }
}
