package io.quarkus.bom.decomposer;

import io.quarkus.domino.scm.ScmRepository;
import org.eclipse.aether.artifact.Artifact;

public class NoopDecomposedBomVisitor implements DecomposedBomVisitor {

    public NoopDecomposedBomVisitor() {
        this(false);
    }

    public NoopDecomposedBomVisitor(boolean skipOriginsWithSingleRelease) {
        this.skipOriginsWithSingleRelease = skipOriginsWithSingleRelease;
    }

    protected final boolean skipOriginsWithSingleRelease;

    @Override
    public void enterBom(Artifact bomArtifact) {
    }

    @Override
    public boolean enterReleaseOrigin(ScmRepository releaseOrigin, int versions) {
        return versions > 1 || !skipOriginsWithSingleRelease;
    }

    @Override
    public void leaveReleaseOrigin(ScmRepository releaseOrigin) throws BomDecomposerException {
    }

    @Override
    public void visitProjectRelease(ProjectRelease release) {
    }

    @Override
    public void leaveBom() throws BomDecomposerException {
    }
}
