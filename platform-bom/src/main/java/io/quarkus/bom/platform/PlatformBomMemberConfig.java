package io.quarkus.bom.platform;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class PlatformBomMemberConfig {

    private Dependency dep;
    private Artifact generatedBomArtifact;

    public PlatformBomMemberConfig(Dependency dep) {
        this.dep = dep;
        this.generatedBomArtifact = dep.getArtifact();
    }

    public void setGeneratedBomArtifact(Artifact generatedBomArtifact) {
        this.generatedBomArtifact = generatedBomArtifact;
    }

    public Artifact originalBomArtifact() {
        return dep.getArtifact();
    }

    public Artifact generatedBomArtifact() {
        return generatedBomArtifact;
    }

    public boolean isBom() {
        return dep.getScope().equals("import");
    }

    public Dependency asDependencyConstraint() {
        return dep;
    }
}
