package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import io.quarkus.domino.scm.ScmRevision;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class DefaultDecomposedBomBuilder implements DecomposedBomBuilder {

    private PomResolver bomSource;
    private Artifact bomArtifact;
    private final Map<ScmRevision, ProjectRelease.Builder> releases = new ConcurrentHashMap<>();

    @Override
    public void bomSource(PomResolver bomSource) {
        this.bomSource = bomSource;
    }

    @Override
    public void bomArtifact(Artifact bomArtifact) {
        this.bomArtifact = bomArtifact;
    }

    @Override
    public void bomDependency(ScmRevision revision, Dependency artifact) {
        releases.computeIfAbsent(revision, ProjectRelease::builder).add(artifact);
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
