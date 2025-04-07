package io.quarkus.domino.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.test.repo.TestArtifactRepo;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.parsers.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public abstract class ManifestingTestBase {

    @TempDir
    static Path testRepoDir;
    static MavenArtifactResolver artifactResolver;

    static void assertMainComponent(Bom bom, ArtifactCoords component, ArtifactCoords... expectedDeps) {
        var purl = PurgingDependencyTreeVisitor.getPurl(component).toString();
        assertThat(bom.getMetadata()).isNotNull();
        Component comp = bom.getMetadata().getComponent();
        assertThat(comp).isNotNull();
        assertThat(purl).isEqualTo(comp.getPurl());
        Dependency dep = null;
        if (bom.getDependencies() != null) {
            for (var d : bom.getDependencies()) {
                if (d.getRef().equals(comp.getBomRef())) {
                    dep = d;
                    break;
                }
            }
        }
        if (dep == null && expectedDeps.length > 0) {
            fail(comp.getBomRef() + " has no dependencies manifested while expected " + expectedDeps.length);
            return;
        }
        final List<Dependency> recordedDeps = dep == null ? List.of() : dep.getDependencies();
        var recordedDepPurls = new ArrayList<String>(recordedDeps.size());
        for (var d : recordedDeps) {
            recordedDepPurls.add(d.getRef());
        }
        var expectedDepPurls = new ArrayList<String>(expectedDeps.length);
        for (var c : expectedDeps) {
            expectedDepPurls.add(PurgingDependencyTreeVisitor.getPurl(c).toString());
        }
        Collections.sort(recordedDepPurls);
        Collections.sort(expectedDepPurls);
        assertThat(recordedDepPurls).isEqualTo(expectedDepPurls);
    }

    static void assertDependencies(Bom bom, ArtifactCoords component, ArtifactCoords... expectedDeps) {
        var purl = PurgingDependencyTreeVisitor.getPurl(component).toString();
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
        if (bom.getDependencies() != null) {
            for (var d : bom.getDependencies()) {
                if (d.getRef().equals(comp.getBomRef())) {
                    dep = d;
                    break;
                }
            }
        }
        if (dep == null && expectedDeps.length > 0) {
            fail(comp.getBomRef() + " has no dependencies manifested while expected " + expectedDeps.length);
            return;
        }
        final List<Dependency> recordedDeps = dep == null ? List.of() : dep.getDependencies();
        var recordedDepPurls = new ArrayList<String>(recordedDeps.size());
        for (var d : recordedDeps) {
            recordedDepPurls.add(d.getRef());
        }
        var expectedDepPurls = new ArrayList<String>(expectedDeps.length);
        for (var c : expectedDeps) {
            expectedDepPurls.add(PurgingDependencyTreeVisitor.getPurl(c).toString());
        }
        Collections.sort(recordedDepPurls);
        Collections.sort(expectedDepPurls);
        assertThat(recordedDepPurls).isEqualTo(expectedDepPurls);
    }

    @BeforeEach
    protected void prepareRepo() {
        var testRepo = TestArtifactRepo.of(testRepoDir);
        artifactResolver = testRepo.getArtifactResolver();
        initRepo(testRepo);
    }

    protected ProjectDependencyConfig.Mutable newConfig() {
        return ProjectDependencyConfig.builder()
                .setWarnOnMissingScm(true)
                .setLegacyScmLocator(true)
                .setVerboseGraphs(true);
    }

    @Test
    public void testManifest() {
        var config = initConfig(newConfig());
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
            if (isLogManifest()) {
                System.out.println(Files.readString(output));
            }
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
        assertBom(bom);
    }

    protected boolean isLogManifest() {
        return false;
    }

    protected abstract void initRepo(TestArtifactRepo repo);

    protected abstract ProjectDependencyConfig initConfig(ProjectDependencyConfig.Mutable config);

    protected abstract void assertBom(Bom bom);
}
