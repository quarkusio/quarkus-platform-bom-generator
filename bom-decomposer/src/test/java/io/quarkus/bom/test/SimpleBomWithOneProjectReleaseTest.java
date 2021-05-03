package io.quarkus.bom.test;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import org.junit.jupiter.api.Test;

public class SimpleBomWithOneProjectReleaseTest extends ProjectInstallerTestSupport {

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

        final DecomposedBom actualBom = bomDecomposer().bomArtifact("org.acme", "acme-bom", "1.0").decompose();

        final ProjectRelease release = ProjectRelease
                .builder(ReleaseIdFactory.create(ReleaseOrigin.Factory.ga("org.acme", "acme-parent"),
                        ReleaseVersion.Factory.version("1.0")))
                .add(aetherArtifact("org.acme:acme-foo::jar:1.0"))
                .add(aetherArtifact("org.acme:acme-bar::jar:1.0"))
                .build();

        final DecomposedBom expectedBom = DecomposedBom.builder().bomArtifact(aetherArtifact("org.acme:acme-bom::pom:1.0"))
                .addRelease(release).build();

        assertEqualBoms(expectedBom, actualBom);
    }
}
