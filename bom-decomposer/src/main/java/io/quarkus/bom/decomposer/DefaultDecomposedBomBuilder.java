package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class DefaultDecomposedBomBuilder implements DecomposedBomBuilder {

    private PomResolver bomSource;
    private Artifact bomArtifact;
    private Map<ReleaseId, List<ProjectDependency>> releases = new HashMap<>();

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
        releases.computeIfAbsent(releaseId, t -> new ArrayList<>()).add(ProjectDependency.create(releaseId, artifact));
    }

    @Override
    public DecomposedBom build() throws BomDecomposerException {
        final DecomposedBom.Builder bomBuilder = DecomposedBom.builder();
        bomBuilder.bomArtifact(bomArtifact);
        bomBuilder.bomSource(bomSource);
        for (Map.Entry<ReleaseId, List<ProjectDependency>> entry : releases.entrySet()) {
            bomBuilder.addRelease(ProjectRelease.create(entry.getKey(), entry.getValue()));
        }
        return bomBuilder.build();
    }

}
