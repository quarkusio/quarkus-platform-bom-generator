package io.quarkus.bom.decomposer;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import org.apache.maven.model.Model;

public class ReleaseIdFactory {
    public static ScmRevision forModel(Model model) {
        final String version = ModelUtils.getVersion(model);
        final String scmOrigin = Util.getScmOrigin(model);
        if (scmOrigin != null) {
            final String scmTag = Util.getScmTag(model);
            return scmTag.isEmpty()
                    || "HEAD".equals(scmTag)
                            //|| !scmTag.contains(version) // sometimes the tag could be '1.4.x' and the version '1.4.1', etc
                            ? ScmRevision.version(ScmRepository.ofUrl(scmOrigin), version)
                            : ScmRevision.tag(ScmRepository.ofUrl(scmOrigin), scmTag);
        }
        return forGav(ModelUtils.getGroupId(model), model.getArtifactId(), version);
    }

    @Deprecated(forRemoval = true)
    public static ReleaseId create(ReleaseOrigin origin, ReleaseVersion version) {
        return new DefaultReleaseId(origin, version);
    }

    public static ScmRevision forGav(String groupId, String artifactId, String version) {
        return ScmRevision.version(ReleaseOrigin.Factory.ga(groupId, artifactId), version);
    }

    public static ScmRevision forGav(String coordsStr) {
        final ArtifactCoords coords = ArtifactCoords.fromString(coordsStr);
        return forGav(coords.getGroupId(), coords.getArtifactId(), coords.getVersion());
    }

    public static ScmRevision forScmAndTag(String scm, String tag) {
        return ScmRevision.tag(ScmRepository.ofUrl(scm), tag);
    }
}
