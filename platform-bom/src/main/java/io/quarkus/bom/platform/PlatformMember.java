package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

public interface PlatformMember {

    List<String> getExtensionGroupIds();

    Artifact previousLastUpdatedBom();

    Artifact lastUpdatedBom();

    Artifact originalBomCoords();

    Artifact generatedBomCoords();

    ArtifactKey key();

    PlatformMemberConfig config();

    PlatformBomMemberConfig bomGeneratorMemberConfig();

    ArtifactCoords stackDescriptorCoords();

    ArtifactCoords descriptorCoords();

    ArtifactCoords propertiesCoords();

    String getVersionProperty();

    DecomposedBom originalDecomposedBom();

    void setOriginalDecomposedBom(DecomposedBom originalBom);

    void setAlignedDecomposedBom(DecomposedBom alignedBom);

    Collection<ArtifactKey> extensionCatalog();

    void setExtensionCatalog(Collection<ArtifactKey> extensionCatalog);
}
