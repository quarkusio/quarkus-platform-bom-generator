package io.quarkus.domino;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.test.repo.TestArtifactRepo;
import io.quarkus.domino.test.repo.TestProject;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BasicBomBasedProjectDependencyTest {

    @TempDir
    static Path testRepoDir;
    static MavenArtifactResolver artifactResolver;

    @BeforeAll
    static void prepareRepo() {
        var testRepo = TestArtifactRepo.of(testRepoDir);
        artifactResolver = testRepo.getArtifactResolver();

        var bazProject = TestProject.of("org.baz", "1.0")
                .setRepoUrl("https://baz.org/lib")
                .setTag("1.0")
                .createMainModule("baz-lib")
                .getProject();
        testRepo.install(bazProject);

        var fooProject10 = TestProject.of("org.foo", "1.0")
                .setRepoUrl("https://foo.org/lib")
                .setTag("1.0")
                .createMainModule("foo-lib")
                .getProject();
        testRepo.install(fooProject10);

        var fooProject20 = TestProject.of("org.foo", "2.0")
                .setRepoUrl("https://foo.org/lib")
                .setTag("2.0")
                .createMainModule("foo-lib")
                .addDependency("org.baz", "baz-lib", "1.0")
                .getProject();
        testRepo.install(fooProject20);

        var barProject = TestProject.of("org.bar", "1.0")
                .setRepoUrl("https://bar.org/lib")
                .setTag("1.0")
                .createParentPom("bar-parent")
                .addModule("bar-lib")
                .addDependency("org.foo", "foo-lib", "1.0")
                .getProject();
        testRepo.install(barProject);

        var acmeProject = TestProject.of("org.acme", "1.0")
                .setRepoUrl("https://acme.org/lib")
                .setTag("1.0");
        var acmeParent = acmeProject.createParentPom("acme-parent");
        acmeParent.addPomModule("acme-bom")
                .addVersionConstraint("acme-common")
                .addVersionConstraint("acme-lib")
                .addVersionConstraint("org.foo", "foo-lib", "2.0")
                .addVersionConstraint("org.bar", "bar-lib", "1.0");
        acmeParent.addModule("acme-common")
                .importBom("acme-bom")
                .addManagedDependency("org.bar", "bar-lib");
        acmeParent.addModule("acme-lib")
                .importBom("acme-bom")
                .addDependency("acme-common");
        testRepo.install(acmeProject);
    }

    private static ProjectDependencyConfig.Mutable newDependencyConfig() {
        return ProjectDependencyConfig.builder()
                .setWarnOnMissingScm(true)
                .setLegacyScmLocator(true)
                .setProjectBom(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
    }

    @Test
    public void completeTree() {

        var depConfig = newDependencyConfig()
                .setProjectArtifacts(List.of(ArtifactCoords.jar("org.acme", "acme-lib", "1.0")));

        var rc = ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(depConfig.build())
                .build()
                .getReleaseCollection();

        assertThat(rc).isNotNull();
        assertThat(rc.getReleases()).hasSize(4);

        var roots = toMap(rc.getRootReleaseRepos());
        assertThat(roots).hasSize(1);

        var release = roots.get(ReleaseIdFactory.forScmAndTag("https://acme.org/lib", "1.0"));
        assertThat(release).isNotNull();
        assertThat(release.getArtifacts()).hasSize(4);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-common", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-lib", "1.0"));
        assertThat(release.getDependencies()).hasSize(1);

        release = release.getDependencies().iterator().next();
        assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://bar.org/lib", "1.0"));
        assertThat(release.getArtifacts()).hasSize(2);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.bar", "bar-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.bar", "bar-lib", "1.0"));
        assertThat(release.getDependencies()).hasSize(1);

        release = release.getDependencies().iterator().next();
        assertThat(release.getArtifacts()).hasSize(1);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.foo", "foo-lib", "2.0"));
        assertThat(release.getDependencies()).hasSize(1);

        release = release.getDependencies().iterator().next();
        assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://baz.org/lib", "1.0"));
        assertThat(release.getArtifacts()).hasSize(1);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.baz", "baz-lib", "1.0"));
        assertThat(release.getDependencies()).isEmpty();
    }

    @Test
    public void onlyManaged() {

        var depConfig = newDependencyConfig()
                .setProjectArtifacts(List.of(ArtifactCoords.jar("org.acme", "acme-lib", "1.0")))
                .setIncludeNonManaged(false);

        var rc = ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(depConfig.build())
                .build()
                .getReleaseCollection();

        assertThat(rc).isNotNull();
        assertThat(rc.getReleases()).hasSize(3);

        var roots = toMap(rc.getRootReleaseRepos());
        assertThat(roots).hasSize(1);
        var release = roots.get(ReleaseIdFactory.forScmAndTag("https://acme.org/lib", "1.0"));
        assertThat(release).isNotNull();
        assertThat(release.getArtifacts()).hasSize(4);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-common", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-lib", "1.0"));
        assertThat(release.getDependencies()).hasSize(1);

        release = release.getDependencies().iterator().next();
        assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://bar.org/lib", "1.0"));
        assertThat(release.getArtifacts()).hasSize(2);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.bar", "bar-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.bar", "bar-lib", "1.0"));
        assertThat(release.getDependencies()).hasSize(1);

        release = release.getDependencies().iterator().next();
        assertThat(release).isNotNull();
        assertThat(release.getArtifacts()).hasSize(1);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.foo", "foo-lib", "2.0"));
        assertThat(release.getDependencies()).isEmpty();
    }

    @Test
    public void level2() {

        var depConfig = newDependencyConfig()
                .setProjectArtifacts(List.of(ArtifactCoords.jar("org.acme", "acme-lib", "1.0")))
                .setLevel(2);

        var rc = ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(depConfig.build())
                .build()
                .getReleaseCollection();

        assertThat(rc).isNotNull();
        assertThat(rc.getReleases()).hasSize(2);

        var roots = rc.getRootReleaseRepos().iterator();
        assertThat(roots).hasNext();
        var release = roots.next();
        assertThat(roots).isExhausted();

        assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://acme.org/lib", "1.0"));
        assertThat(release.getArtifacts()).hasSize(4);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-common", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-lib", "1.0"));
        assertThat(release.getDependencies()).hasSize(1);

        release = release.getDependencies().iterator().next();
        assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://bar.org/lib", "1.0"));
        assertThat(release.getArtifacts()).hasSize(2);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.bar", "bar-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.bar", "bar-lib", "1.0"));
        assertThat(release.getDependencies()).isEmpty();
    }

    private static Map<ScmRevision, ReleaseRepo> toMap(Iterable<ReleaseRepo> releases) {
        var map = new HashMap<ScmRevision, ReleaseRepo>();
        for (var r : releases) {
            map.put(r.getRevision(), r);
        }
        return map;
    }
}
