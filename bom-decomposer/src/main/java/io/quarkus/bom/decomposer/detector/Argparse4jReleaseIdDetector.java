package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class Argparse4jReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("net.sourceforge.argparse4j")) {
            var releaseId = releaseResolver.defaultReleaseId(artifact);
            var origin = releaseId.getRepository();
            if (!origin.hasUrl() || !origin.getUrl().equals("https://github.com/argparse4j/argparse4j")) {
                origin = ScmRepository.ofUrl("https://github.com/argparse4j/argparse4j");
            }
            var version = releaseId.getValue();
            if (!version.startsWith("argparse4j-")) {
                version = "argparse4j-" + version;
            }
            return ScmRevision.tag(origin, version);
        }
        return null;
    }

}
