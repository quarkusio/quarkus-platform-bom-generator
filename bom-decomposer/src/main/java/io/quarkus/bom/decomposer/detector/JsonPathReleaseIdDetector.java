package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class JsonPathReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("com.jayway.jsonpath")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            var origin = releaseId.getRepository();
            if (!origin.hasUrl() || !releaseId.getRepository().getUrl().equals("https://github.com/jayway/JsonPath")) {
                origin = ScmRepository.ofUrl("https://github.com/jayway/JsonPath");
            }
            String tag = releaseId.getValue();
            if (!tag.startsWith("json-path-")) {
                tag = "json-path-" + artifact.getVersion();
            }
            return ScmRevision.tag(origin, tag);
        }
        return null;
    }
}
