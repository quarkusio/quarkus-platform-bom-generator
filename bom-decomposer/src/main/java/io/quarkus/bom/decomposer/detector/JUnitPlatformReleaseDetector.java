package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class JUnitPlatformReleaseDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (artifact.getGroupId().startsWith("org.junit")) {
            if (artifact.getVersion().startsWith("5.")) {
                return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/junit-team/junit5"),
                        "r" + artifact.getVersion());
            }
            if (artifact.getVersion().startsWith("1.")) {
                return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/junit-team/junit5"),
                        "r" + artifact.getVersion().replace("1.", "5."));
            }
        }
        return null;
    }
}
