package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class JaxbRiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("com.sun.xml.bind")
                && !artifact.getGroupId().equals("org.glassfish.jaxb")) {
            return null;
        }
        var releaseId = releaseResolver.readRevisionFromPom(artifact);
        String version = releaseId.getValue();
        if (version.endsWith("-RI")) {
            return releaseId;
        }
        var origin = releaseId.getRepository();
        if (!origin.hasUrl() || !origin.getUrl().startsWith("https://")) {
            origin = ScmRepository.ofUrl("https://github.com/eclipse-ee4j/jaxb-ri");
        }

        return ScmRevision.tag(origin, version + "-RI");
    }

}
