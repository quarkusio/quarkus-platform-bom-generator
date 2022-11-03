package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class Argparse4jReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("net.sourceforge.argparse4j")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseOrigin origin = releaseId.origin();
            if (!origin.toString().equals("https://github.com/argparse4j/argparse4j")) {
                origin = ReleaseOrigin.Factory.scmConnection("https://github.com/argparse4j/argparse4j");
            }
            ReleaseVersion version = releaseId.version();
            if (!version.toString().startsWith("argparse4j-")) {
                version = ReleaseVersion.Factory.tag("argparse4j-" + version.toString());
            }
            return ReleaseIdFactory.create(origin, version);
        }
        return null;
    }

}
