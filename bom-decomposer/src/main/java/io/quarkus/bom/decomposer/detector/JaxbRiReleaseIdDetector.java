package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JaxbRiReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("com.sun.xml.bind")
                && !artifact.getGroupId().equals("org.glassfish.jaxb")) {
            return null;
        }
        final ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
        String version = releaseId.version().asString();
        if (version.endsWith("-RI")) {
            return releaseId;
        }
        ReleaseOrigin origin = releaseId.origin();
        if (!origin.toString().startsWith("https://")) {
            origin = ReleaseOrigin.Factory.scmConnection("https://github.com/eclipse-ee4j/jaxb-ri");
        }

        return ReleaseIdFactory.create(origin, ReleaseVersion.Factory.tag(version + "-RI"));
    }

}
