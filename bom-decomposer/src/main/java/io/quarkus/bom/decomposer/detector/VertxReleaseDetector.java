package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class VertxReleaseDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("io.vertx")) {
            return null;
        }
        if (artifact.getArtifactId().equals("vertx-docgen")) {
            return null;
        }
        return ScmRevision.version(ScmRepository.ofId("io.vertx"),
                ModelUtils.getVersion(idResolver.readPom(artifact)));
    }
}
