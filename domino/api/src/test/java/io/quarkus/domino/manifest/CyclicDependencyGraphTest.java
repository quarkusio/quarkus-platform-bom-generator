package io.quarkus.domino.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.test.repo.TestArtifactRepo;
import io.quarkus.domino.test.repo.TestProject;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.parsers.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CyclicDependencyGraphTest {

    @TempDir
    static Path testRepoDir;
    static MavenArtifactResolver artifactResolver;

    @BeforeAll
    static void prepareRepo() {
        var testRepo = TestArtifactRepo.of(testRepoDir);
        artifactResolver = testRepo.getArtifactResolver();

        var tcnativeProject = TestProject.of("io.netty", "1.0")
                .setRepoUrl("https://netty.io/tcnative")
                .setTag("1.0");
        tcnativeProject.createMainModule("netty-tcnative-boringssl-static")
                .addClassifier("linux-aarch_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-aarch_64", " 1.0"))
                .addClassifier("linux-x86_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", " 1.0"))
                .addClassifier("osx-aarch_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-aarch_64", " 1.0"))
                .addClassifier("osx-x86_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-x86_64", " 1.0"))
                .addClassifier("windows-x86_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "windows-x86_64", " 1.0"));
        testRepo.install(tcnativeProject);

        var appProject = TestProject.of("org.acme", "1.0")
                .setRepoUrl("https://acme.org/app")
                .setTag("1.0");
        appProject.createMainModule("acme-app")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", "1.0"));
        testRepo.install(appProject);
    }

    private static ProjectDependencyConfig.Mutable newDependencyConfig() {
        return ProjectDependencyConfig.builder()
                .setWarnOnMissingScm(true)
                .setLegacyScmLocator(true);
    }

    @Test
    public void dependencyGraph() {
        var config = newDependencyConfig()
                .setProjectArtifacts(List.of(ArtifactCoords.jar("org.acme", "acme-app", "1.0")));

        final Bom bom;
        Path output = null;
        try {
            output = Files.createTempFile("domino-test", "sbom");

            ProjectDependencyResolver.builder()
                    .setArtifactResolver(artifactResolver)
                    .setDependencyConfig(config)
                    .addDependencyTreeVisitor(new SbomGeneratingDependencyVisitor(
                            SbomGenerator.builder()
                                    .setArtifactResolver(artifactResolver)
                                    .setOutputFile(output)
                                    .setEnableTransformers(false),
                            config))
                    .build()
                    .resolveDependencies();

            try (BufferedReader reader = Files.newBufferedReader(output)) {
                bom = new JsonParser().parse(reader);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (output != null) {
                output.toFile().deleteOnExit();
            }
        }

        assertDependencies(bom, ArtifactCoords.jar("org.acme", "acme-app", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", "1.0"));

        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-aarch_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-aarch_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-x86_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "windows-x86_64", "1.0"));

        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-aarch_64", "1.0"));
        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-aarch_64", "1.0"));
        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-x86_64", "1.0"));
        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "windows-x86_64", "1.0"));
    }

    private static void assertDependencies(Bom bom, ArtifactCoords compCoords, ArtifactCoords... expectedDeps) {
        var purl = PurgingDependencyTreeVisitor.getPurl(compCoords).toString();
        Component comp = null;
        for (var c : bom.getComponents()) {
            if (c.getPurl().equals(purl)) {
                comp = c;
                break;
            }
        }
        if (comp == null) {
            fail("Failed to locate " + purl);
            return;
        }
        Dependency dep = null;
        for (var d : bom.getDependencies()) {
            if (d.getRef().equals(comp.getBomRef())) {
                dep = d;
                break;
            }
        }
        if (dep == null && expectedDeps.length > 0) {
            fail(comp.getBomRef() + " has no dependencies manifested while expected " + expectedDeps.length);
            return;
        }
        final List<Dependency> recordedDeps = dep == null ? List.of() : dep.getDependencies();
        var recordedDepPurls = new HashSet<String>(recordedDeps.size());
        for (var d : recordedDeps) {
            recordedDepPurls.add(d.getRef());
        }
        var expectedDepPurls = new HashSet<String>(expectedDeps.length);
        for (var c : expectedDeps) {
            expectedDepPurls.add(PurgingDependencyTreeVisitor.getPurl(c).toString());
        }
        assertThat(recordedDepPurls).isEqualTo(expectedDepPurls);
    }
}
