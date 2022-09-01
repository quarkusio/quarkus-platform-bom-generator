package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class GuavaReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.google.guava")) {
            if (artifact.getArtifactId().equals("failureaccess")) {
                // THIS DOESN'T WORK, the tag is off
                return ReleaseIdFactory.forScmAndTag("https://github.com/google/guava", artifact.getVersion());
            }
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String version = releaseId.version().asString();
            if (version.endsWith("-jre")) {
                version = version.substring(0, version.length() - "-jre".length());
            } else if (version.endsWith("-android")) {
                version = version.substring(0, version.length() - "-android".length());
            }
            return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.version("v" + version));
        }
        return null;
    }
}
