package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class DefaultDecomposedBomBuilder implements DecomposedBomBuilder {

    private PomResolver bomSource;
    private Artifact bomArtifact;
    private Map<ReleaseId, ProjectRelease.Builder> releases = new HashMap<>();

    @Override
    public void bomSource(PomResolver bomSource) {
        this.bomSource = bomSource;
    }

    @Override
    public void bomArtifact(Artifact bomArtifact) {
        this.bomArtifact = bomArtifact;
    }

    @Override
    public void bomDependency(ReleaseId releaseId, Dependency artifact) throws BomDecomposerException {
        releases.computeIfAbsent(releaseId, t -> ProjectRelease.builder(t)).add(artifact);
    }

    @Override
    public DecomposedBom build() throws BomDecomposerException {
        final DecomposedBom.Builder bomBuilder = DecomposedBom.builder();
        bomBuilder.bomArtifact(bomArtifact);
        bomBuilder.bomSource(bomSource);
        for (ProjectRelease.Builder builder : releases.values()) {
            bomBuilder.addRelease(builder.build());
        }
        return bomBuilder.build();
    }

}
