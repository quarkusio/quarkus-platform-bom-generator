package io.quarkus.domino;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.domino.test.repo.TestArtifactRepo;
import io.quarkus.domino.test.repo.TestProject;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestArtifactRepoTest {

    @TempDir
    Path testRepoDir;

    @Test
    public void multimodule() throws Exception {

        var project = TestProject.of("org.acme", "1.0");
        var acmeParent = project.createParentPom("acme-parent");
        acmeParent.addModule("acme-bom")
                .setPackaging("pom")
                .addVersionConstraint("acme-api")
                .addVersionConstraint("acme-lib");
        acmeParent.addModule("acme-api")
                .importBom("acme-bom");
        acmeParent.addModule("acme-lib")
                .importBom("acme-bom")
                .addDependency("acme-api");

        var repo = TestArtifactRepo.of(testRepoDir)
                .install(project);

        var resolver = repo.getArtifactResolver();
        resolver.resolve(new DefaultArtifact("org.acme", "acme-parent", "pom", "1.0"));
        resolver.resolve(new DefaultArtifact("org.acme", "acme-bom", "pom", "1.0"));
        resolver.resolve(new DefaultArtifact("org.acme", "acme-api", "pom", "1.0"));

        final Artifact acmeLib = new DefaultArtifact("org.acme", "acme-lib", "jar", "1.0");
        resolver.resolve(acmeLib);

        var acmeLibDescr = resolver.resolveDescriptor(acmeLib);
        assertThat((acmeLibDescr)).isNotNull();
        assertThat(acmeLibDescr.getManagedDependencies()).hasSize(2);
        var d = acmeLibDescr.getManagedDependencies().get(0);
        assertThat(d.getArtifact().getGroupId()).isEqualTo("org.acme");
        assertThat(d.getArtifact().getArtifactId()).isEqualTo("acme-api");
        assertThat(d.getArtifact().getVersion()).isEqualTo("1.0");
        d = acmeLibDescr.getManagedDependencies().get(1);
        assertThat(d.getArtifact().getGroupId()).isEqualTo("org.acme");
        assertThat(d.getArtifact().getArtifactId()).isEqualTo("acme-lib");
        assertThat(d.getArtifact().getVersion()).isEqualTo("1.0");
        assertThat(acmeLibDescr.getDependencies()).hasSize(1);
        d = acmeLibDescr.getDependencies().get(0);
        assertThat(d.getArtifact().getGroupId()).isEqualTo("org.acme");
        assertThat(d.getArtifact().getArtifactId()).isEqualTo("acme-api");
        assertThat(d.getArtifact().getVersion()).isEqualTo("1.0");

        var node = resolver.resolveDependencies(acmeLib, List.of()).getRoot();
        assertThat(node).isNotNull();
        assertThat(node.getArtifact().getArtifactId()).isEqualTo("acme-lib");
        assertThat(node.getChildren()).hasSize(1);
        node = node.getChildren().get(0);
        assertThat(node.getArtifact().getArtifactId()).isEqualTo("acme-api");
        assertThat(node.getChildren()).hasSize(0);
    }
}
