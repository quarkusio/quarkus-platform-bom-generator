package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ScmRevisionResolver;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class ReactiveStreamsReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        if (artifact.getGroupId().equals("org.reactivestreams")) {
            var releaseId = releaseResolver.readRevisionFromPom(artifact);
            String repoUrl = releaseId.getRepository().getId();
            String tag = releaseId.getValue();
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
