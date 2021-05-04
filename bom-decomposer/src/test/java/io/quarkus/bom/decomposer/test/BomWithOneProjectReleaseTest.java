package io.quarkus.bom.decomposer.test;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.test.ProjectInstallerTestSupport;
import org.junit.jupiter.api.Test;

public class BomWithOneProjectReleaseTest extends ProjectInstallerTestSupport {

    @Test
    public void test() throws Exception {
        projectWithParentPom("org.acme:acme-parent::pom:1.0")
                .artifactId("acme-foo")
                .artifactId("acme-bar")
                .install();

        pomInstaller("org.acme:acme-bom::pom:1.0")
                .managedArtifactId("acme-foo")
                .managedArtifactId("acme-bar")
                .install();

        final ProjectRelease release = ProjectRelease
                .builder(ReleaseIdFactory.forGav("org.acme:acme-parent::pom:1.0"))
                .add(aetherArtifact("org.acme:acme-foo::jar:1.0"))
                .add(aetherArtifact("org.acme:acme-bar::jar:1.0"))
                .build();

        final DecomposedBom expectedBom = DecomposedBom.builder().bomArtifact(aetherArtifact("org.acme:acme-bom::pom:1.0"))
                .addRelease(release).build();

        assertEqualBoms(expectedBom, bomDecomposer().bomArtifact("org.acme", "acme-bom", "1.0").decompose());
    }
}
