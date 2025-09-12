package io.quarkus.domino;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NonCorePlatformMemberDependenciesTest {

    @TempDir
    static Path testRepoDir;
    static MavenArtifactResolver artifactResolver;
    static Map<ScmRevision, Consumer<ReleaseRepo>> releaseAssertions;

    @BeforeAll
    static void prepareRepo() {
        var testRepo = TestArtifactRepo.of(testRepoDir);
        artifactResolver = testRepo.getArtifactResolver();

        var byteUtilsProject = TestProject.of("org.bytes", "1.0")
                .setRepoUrl("https://bytes.org/code")
                .setTag("1.0");
        var byteUtilsLib = byteUtilsProject.createMainModule("byte-utils");

        var commonsIoProject = TestProject.of("org.commons.io", "1.0")
                .setRepoUrl("https://commons.org/code/io")
                .setTag("1.0");
        var commonsIoLib = commonsIoProject.createMainModule("commons-io")
                .addDependency(byteUtilsLib);

        var quarkusProject = TestProject.of("io.quarkus", "1.0")
                .setRepoUrl("https://quarkus.io/code")
                .setTag("1.0");
        var quarkusParent = quarkusProject.createParentPom("quarkus-parent");
        var quarkusBom = quarkusParent.addPomModule("quarkus-bom")
                .addVersionConstraint("quarkus-core");
        var quarkusBuildParent = quarkusParent.addPomModule("quarkus-build-parent")
                .importBom(quarkusBom);
        var quarkusCore = quarkusBuildParent.addModule("quarkus-core")
                .addDependency(commonsIoLib);

        var filesLibProject = TestProject.of("org.files", "1.0")
                .setRepoUrl("https://files.org/code")
                .setTag("1.0");
        var filesLib = filesLibProject.createMainModule("file-utils");

        var xmlLibProject = TestProject.of("org.xml", "1.0")
                .setRepoUrl("https://xml.org/code")
                .setTag("1.0");
        var xmlLib = xmlLibProject.createMainModule("xml-lib")
                .addDependency(filesLib);

        var camelProject = TestProject.of("org.camel", "1.0")
                .setRepoUrl("https://camel.org/code")
                .setTag("1.0");
        var camelParent = camelProject.createParentPom("camel-parent");
        var camelBom = camelParent.addPomModule("camel-bom")
                .addVersionConstraint("camel-core")
                .addVersionConstraint("camel-xml-lib")
                .addVersionConstraint(xmlLib)
                .addVersionConstraint(commonsIoLib);
        var camelBuildParent = camelParent.addPomModule("camel-build-parent")
                .importBom(camelBom);
        var camelCore = camelBuildParent.addModule("camel-core")
                .addDependency(quarkusCore);
        var camelXmlLib = camelBuildParent.addModule("camel-xml-lib")
                .addManagedDependency(camelCore)
                .addManagedDependency(xmlLib);

        testRepo.install(quarkusProject)
                .install(camelProject)
                .install(filesLibProject)
                .install(xmlLibProject)
                .install(commonsIoProject)
                .install(byteUtilsProject);

        releaseAssertions = Map.<ScmRevision, Consumer<ReleaseRepo>> of(
                ReleaseIdFactory.forScmAndTag("https://quarkus.io/code", "1.0"),
                release -> {
                    assertThat(release.getRevision())
                            .isEqualTo(ReleaseIdFactory.forScmAndTag("https://quarkus.io/code", "1.0"));
                    assertThat(release.getArtifacts()).hasSize(4);
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("io.quarkus", "quarkus-parent", "1.0"));
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("io.quarkus", "quarkus-bom", "1.0"));
                    assertThat(release.getArtifacts())
                            .containsKey(ArtifactCoords.pom("io.quarkus", "quarkus-build-parent", "1.0"));
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("io.quarkus", "quarkus-core", "1.0"));
                    assertThat(release.getDependencies()).hasSize(1);
                },
                ReleaseIdFactory.forScmAndTag("https://camel.org/code", "1.0"),
                release -> {
                    assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://camel.org/code", "1.0"));
                    assertThat(release.getArtifacts()).hasSize(5);
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.camel", "camel-parent", "1.0"));
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.camel", "camel-bom", "1.0"));
                    assertThat(release.getArtifacts())
                            .containsKey(ArtifactCoords.pom("org.camel", "camel-build-parent", "1.0"));
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.camel", "camel-core", "1.0"));
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.camel", "camel-xml-lib", "1.0"));
                    assertThat(release.getDependencies()).hasSize(2);
                },
                ReleaseIdFactory.forScmAndTag("https://xml.org/code", "1.0"),
                release -> {
                    assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://xml.org/code", "1.0"));
                    assertThat(release.getArtifacts()).hasSize(1);
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.xml", "xml-lib", "1.0"));
                },
                ReleaseIdFactory.forScmAndTag("https://commons.org/code/io", "1.0"),
                release -> {
                    assertThat(release.getRevision())
                            .isEqualTo(ReleaseIdFactory.forScmAndTag("https://commons.org/code/io", "1.0"));
                    assertThat(release.getArtifacts()).hasSize(1);
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.commons.io", "commons-io", "1.0"));
                },
                ReleaseIdFactory.forScmAndTag("https://bytes.org/code", "1.0"),
                release -> {
                    assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://bytes.org/code", "1.0"));
                    assertThat(release.getArtifacts()).hasSize(1);
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.bytes", "byte-utils", "1.0"));
                    assertThat(release.getDependencies()).isEmpty();
                },
                ReleaseIdFactory.forScmAndTag("https://files.org/code", "1.0"),
                release -> {
                    assertThat(release.getRevision()).isEqualTo(ReleaseIdFactory.forScmAndTag("https://files.org/code", "1.0"));
                    assertThat(release.getArtifacts()).hasSize(1);
                    assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.files", "file-utils", "1.0"));
                    assertThat(release.getDependencies()).isEmpty();
                });
    }

    private static ProjectDependencyConfig.Mutable newDependencyConfig() {
        return ProjectDependencyConfig.builder()
                .setWarnOnMissingScm(true)
                .setLegacyScmLocator(true)
                .setProjectBom(ArtifactCoords.pom("org.camel", "camel-bom", "1.0"))
                .setNonProjectBoms(List.of(ArtifactCoords.pom("io.quarkus", "quarkus-bom", "1.0")));
    }

    @Test
    public void managedOnly() {

        var depConfig = newDependencyConfig()
                .setIncludeNonManaged(false)
                .setProjectArtifacts(List.of(
                        ArtifactCoords.jar("org.camel", "camel-core", "1.0"),
                        ArtifactCoords.jar("org.camel", "camel-xml-lib", "1.0")));

        var rc = ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(depConfig.build())
                .build()
                .getReleaseCollection();
        assertThat(rc).isNotNull();

        var releaseAssertions = new HashMap<ScmRevision, Consumer<ReleaseRepo>>();
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://quarkus.io/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://camel.org/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://xml.org/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://commons.org/code/io", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));

        assertReleaseDependencies(rc, releaseAssertions);
    }

    @Test
    public void rootUniverse() {

        var depConfig = newDependencyConfig()
                .setIncludePatterns(List.of(ArtifactCoords.fromString("*:*:*")));

        ProjectDependencyResolver projectDependencyResolver = ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(depConfig.build())
                .build();
        projectDependencyResolver.resolveDependencies();

        Iterable<ArtifactCoords> rootArtifacts = projectDependencyResolver.getProjectArtifacts();
        List<ArtifactCoords> quarkusIoArtifacts = StreamSupport.stream(rootArtifacts.spliterator(), false)
                .filter(a -> a.getGroupId().equals("io.quarkus"))
                .collect(Collectors.toList());
        /* In spite that we include everything, there should be no io.quarkus artifact in the result */
        assertThat(quarkusIoArtifacts).isEmpty();
    }

    @Test
    public void completeTree() {

        var depConfig = newDependencyConfig()
                .setProjectArtifacts(List.of(
                        ArtifactCoords.jar("org.camel", "camel-core", "1.0"),
                        ArtifactCoords.jar("org.camel", "camel-xml-lib", "1.0")));

        var rc = ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(depConfig.build())
                .build()
                .getReleaseCollection();
        assertThat(rc).isNotNull();

        var releaseAssertions = new HashMap<ScmRevision, Consumer<ReleaseRepo>>();
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://quarkus.io/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://camel.org/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://xml.org/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://commons.org/code/io", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://bytes.org/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));
        releaseAssertions.computeIfAbsent(ReleaseIdFactory.forScmAndTag("https://files.org/code", "1.0"),
                k -> NonCorePlatformMemberDependenciesTest.releaseAssertions.get(k));

        assertReleaseDependencies(rc, releaseAssertions);
    }

    private static void assertReleaseDependencies(ReleaseCollection rc,
            Map<ScmRevision, Consumer<ReleaseRepo>> releaseAssertions) {
        for (ReleaseRepo r : rc.getReleases()) {
            var assertions = releaseAssertions.remove(r.getRevision());
            if (assertions == null) {
                fail("Unexpected release " + r.getRevision());
            } else {
                assertions.accept(r);
            }
        }

        if (!releaseAssertions.isEmpty()) {
            var i = releaseAssertions.keySet().iterator();
            var sb = new StringBuilder();
            sb.append("Missing release(s): ").append(i.next());
            while (i.hasNext()) {
                sb.append(", ").append(i.next());
            }
            fail(sb.toString());
        }
    }
}
