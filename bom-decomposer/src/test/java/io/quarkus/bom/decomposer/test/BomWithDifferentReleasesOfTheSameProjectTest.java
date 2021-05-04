package io.quarkus.bom.decomposer.test;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.test.ProjectInstallerTestSupport;
import org.junit.jupiter.api.Test;

public class BomWithDifferentReleasesOfTheSameProjectTest extends ProjectInstallerTestSupport {

    @Test
    public void test() throws Exception {
        projectWithParentPom("org.red:red-parent::pom:1.0")
                .artifactId("red-foo")
                .artifactId("red-bar")
                .install();
        projectWithParentPom("org.red:red-parent::pom:1.1")
                .artifactId("red-foo")
                .artifactId("red-bar")
                .install();

        projectWithParentPom("org.blue:blue-parent::pom:1.0")
                .artifactId("blue-foo")
                .artifactId("blue-bar")
                .install();
        projectWithParentPom("org.blue:blue-parent::pom:1.1")
                .artifactId("blue-foo")
                .artifactId("blue-bar")
                .install();

        pomInstaller("org.acme:acme-bom::pom:1.0")
                .managedDep("org.red:red-foo::jar:1.0")
                .managedDep("org.red:red-bar::jar:1.1")
                .managedDep("org.blue:blue-foo::jar:1.1")
                .managedDep("org.blue:blue-bar::jar:1.0")
                .install();

        final ProjectRelease red10 = ProjectRelease
                .builder(ReleaseIdFactory.forGav("org.red:red-parent::pom:1.0"))
                .add(aetherArtifact("org.red:red-foo::jar:1.0"))
                .build();
        final ProjectRelease red11 = ProjectRelease
                .builder(ReleaseIdFactory.forGav("org.red:red-parent::pom:1.1"))
                .add(aetherArtifact("org.red:red-bar::jar:1.1"))
                .build();
        final ProjectRelease blue10 = ProjectRelease
                .builder(ReleaseIdFactory.forGav("org.blue:blue-parent::pom:1.0"))
                .add(aetherArtifact("org.blue:blue-bar::jar:1.0"))
                .build();
        final ProjectRelease blue11 = ProjectRelease
                .builder(ReleaseIdFactory.forGav("org.blue:blue-parent::pom:1.1"))
                .add(aetherArtifact("org.blue:blue-foo::jar:1.1"))
                .build();

        final DecomposedBom expectedBom = DecomposedBom.builder()
                .bomArtifact(aetherArtifact("org.acme:acme-bom::pom:1.0"))
                .addRelease(red10)
                .addRelease(red11)
                .addRelease(blue10)
                .addRelease(blue11)
                .build();

        assertEqualBoms(expectedBom, bomDecomposer().bomArtifact("org.acme", "acme-bom", "1.0").decompose());
    }
}
