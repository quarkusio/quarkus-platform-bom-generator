package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class JakartaElReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("jakarta.el")
                || artifact.getArtifactId().equals("jakarta.el") && artifact.getGroupId().equals("org.glassfish")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            if (releaseId.getRepository().hasUrl() && !releaseId.getRepository().getUrl().contains("eclipse-ee4j")) {
                return releaseId;
            }
            String suffix = artifact.getVersion().startsWith("5.") ? "-RELEASE-api" : "-impl";
            return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/jakartaee/expression-language"),
                    artifact.getVersion() + suffix);
        }
        return null;
    }

}
