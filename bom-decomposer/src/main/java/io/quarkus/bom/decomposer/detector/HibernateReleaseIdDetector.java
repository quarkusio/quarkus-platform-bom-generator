package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import org.eclipse.aether.artifact.Artifact;

public class HibernateReleaseIdDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("org.hibernate")) {
            return null;
        }
        var releaseId = idResolver.readRevisionFromPom(artifact);
        final String repo = releaseId.getRepository().toString();
        final String version = releaseId.getValue();
        if (!repo.endsWith("hibernate-orm") && !repo.endsWith("hibernate-reactive")
                || !version.endsWith(".Final")) {
            return releaseId;
        }
        return ScmRevision.tag(releaseId.getRepository(), version.substring(0, version.length() - ".Final".length()));
    }
}
