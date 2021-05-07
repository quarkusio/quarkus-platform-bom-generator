package io.quarkus.bom.platform;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class PlatformBomMemberConfig {

    private Dependency bomDep;
    private Artifact generatedBomArtifact;

    public PlatformBomMemberConfig(Dependency dep) {
        this.bomDep = dep;
        this.generatedBomArtifact = dep.getArtifact();
    }

    public void setGeneratedBomArtifact(Artifact generatedBomArtifact) {
        this.generatedBomArtifact = generatedBomArtifact;
    }

    public Artifact originalBomArtifact() {
        return bomDep.getArtifact();
    }

    public Artifact generatedBomArtifact() {
        return generatedBomArtifact;
    }

    public boolean isBom() {
        return bomDep.getScope().equals("import");
    }

    public Dependency asDependencyConstraint() {
        return bomDep;
    }
}
