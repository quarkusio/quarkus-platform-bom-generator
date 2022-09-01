package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import org.eclipse.aether.artifact.Artifact;

public class ReactiveStreamsReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.reactivestreams")) {
            ReleaseId releaseId = releaseResolver.defaultReleaseId(artifact);
            String repoUrl = releaseId.origin().toString();
            String tag = releaseId.version().asString();
            if (!repoUrl.endsWith("reactive-streams-jvm")) {
                repoUrl = repoUrl.substring(0, repoUrl.lastIndexOf('/') + 1);
                repoUrl += "reactive-streams-jvm";
            }
            if (tag.charAt(0) != 'v') {
                tag = "v" + tag;
            }
            return ReleaseIdFactory.forScmAndTag(repoUrl, tag);
        }
        return null;
    }

}
