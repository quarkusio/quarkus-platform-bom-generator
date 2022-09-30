package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.eclipse.aether.artifact.Artifact;

public class JsonPathReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.jayway.jsonpath")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            ReleaseOrigin origin = releaseId.origin();
            if (!releaseId.origin().toString().equals("https://github.com/jayway/JsonPath")) {
                origin = ReleaseOrigin.Factory.scmConnection("https://github.com/jayway/JsonPath");
            }
            ReleaseVersion version = releaseId.version();
            if (!version.toString().startsWith("json-path-")) {
                version = ReleaseVersion.Factory.tag("json-path-" + artifact.getVersion());
            }
            return ReleaseIdFactory.create(origin, version);
        }
        return null;
    }
}
