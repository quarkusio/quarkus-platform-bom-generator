package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import io.quarkus.maven.ArtifactKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class DefaultDecomposedBomBuilder implements DecomposedBomBuilder {

    private PomResolver bomSource;
    private Artifact bomArtifact;
    private Map<ReleaseId, Map<ArtifactKey, ProjectDependency>> releases = new HashMap<>();

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
        final ProjectDependency d = ProjectDependency.create(releaseId, artifact);
        releases.computeIfAbsent(releaseId, t -> new LinkedHashMap<>()).putIfAbsent(d.key(), d);
    }

    @Override
    public DecomposedBom build() throws BomDecomposerException {
        final DecomposedBom.Builder bomBuilder = DecomposedBom.builder();
        bomBuilder.bomArtifact(bomArtifact);
        bomBuilder.bomSource(bomSource);
        for (Map.Entry<ReleaseId, Map<ArtifactKey, ProjectDependency>> entry : releases.entrySet()) {
            bomBuilder.addRelease(ProjectRelease.create(entry.getKey(), new ArrayList<>(entry.getValue().values())));
        }
        return bomBuilder.build();
    }

}
