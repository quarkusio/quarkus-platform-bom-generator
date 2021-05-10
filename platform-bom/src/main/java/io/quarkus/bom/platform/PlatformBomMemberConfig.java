package io.quarkus.bom.platform;

import java.util.Collections;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class PlatformBomMemberConfig {

    private final Dependency bomDep;
    private final List<Dependency> dm;
    private String key;
    private Artifact generatedBomArtifact;

    public PlatformBomMemberConfig(Dependency bomDep) {
        this.bomDep = bomDep;
        this.dm = Collections.singletonList(bomDep);
        this.generatedBomArtifact = bomDep == null ? null : bomDep.getArtifact();
        this.key = bomDep.getArtifact().getGroupId() + ":" + bomDep.getArtifact().getArtifactId();
    }

    public PlatformBomMemberConfig(List<Dependency> dm) {
        this.bomDep = null;
        this.dm = dm;
    }

    public String key() {
        if (key == null) {
            key = bomDep == null ? generatedBomArtifact.getGroupId() + ":" + generatedBomArtifact.getArtifactId()
                    : bomDep.getArtifact().getGroupId() + ":" + bomDep.getArtifact().getArtifactId();
        }
        return key;
    }

    public void setGeneratedBomArtifact(Artifact generatedBomArtifact) {
        this.generatedBomArtifact = generatedBomArtifact;
    }

    public Artifact originalBomArtifact() {
        return bomDep == null ? null : bomDep.getArtifact();
    }

    public Artifact generatedBomArtifact() {
        return generatedBomArtifact;
    }

    public boolean isBom() {
        return bomDep == null ? false : bomDep.getScope().equals("import");
    }

    public List<Dependency> asDependencyConstraints() {
        return dm;
    }
}
