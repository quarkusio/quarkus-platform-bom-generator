package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public interface PlatformMember {

    List<String> getExtensionGroupIds();

    List<String> getOwnGroupIds();

    Artifact previousLastUpdatedBom();

    Artifact latestBomRelease();

    Artifact getInputBom();

    Artifact getConfiguredPlatformBom();

    Artifact getGeneratedPlatformBom();

    boolean isIncrementBomVersionOnChange();

    ArtifactKey key();

    PlatformMemberConfig config();

    List<Dependency> inputConstraints();

    ArtifactCoords descriptorCoords();

    ArtifactCoords propertiesCoords();

    String getVersionProperty();

    DecomposedBom originalDecomposedBom();

    void setOriginalDecomposedBom(DecomposedBom originalBom);

    void setAlignedDecomposedBom(DecomposedBom alignedBom);

    DecomposedBom getAlignedDecomposedBom();

    Collection<ArtifactKey> extensionCatalog();

    void setExtensionCatalog(Collection<ArtifactKey> extensionCatalog);
}
