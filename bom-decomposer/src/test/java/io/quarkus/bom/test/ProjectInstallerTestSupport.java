package io.quarkus.bom.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class ProjectInstallerTestSupport {

    private static MessageWriter log = MessageWriter.info();
    private static Path workDir;
    private static Path testRepoDir;
    private static MavenArtifactResolver resolver;

    @BeforeAll
    public static void setupProjectInstaller() throws Exception {
        workDir = Files.createTempDirectory("qs");
        testRepoDir = workDir.resolve("test-repo");
        Files.createDirectories(testRepoDir);

        resolver = MavenArtifactResolver.builder()
                .setOffline(true)
                .setLocalRepository(testRepoDir.toString())
                .setWorkspaceDiscovery(false)
                .build();
    }

    @AfterAll
    public static void cleanUpAfterAll() {
        IoUtils.recursiveDelete(workDir);
    }

    protected ProjectReleaseInstaller projectWithParentPom(String coords) {
        return ProjectReleaseInstaller.forParentPom(coords).resolver(resolver);
    }

    protected PomInstaller pomInstaller(String coords) {
        return PomInstaller.forCoords(coords).resolver(resolver);
    }

    protected BomDecomposer.BomDecomposerConfig bomDecomposer() {
        return BomDecomposer.config().mavenArtifactResolver(ArtifactResolverProvider.get(resolver)).logger(log);
    }

    protected Artifact resolve(String coordsStr) {
        final AppArtifactCoords coords = AppArtifactCoords.fromString(coordsStr);
        return resolve(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                coords.getType(), coords.getVersion()));
    }

    protected Artifact resolve(Artifact a) {
        try {
            return resolver.resolve(a).getArtifact();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to resolve " + a, e);
        }
    }

    public static void assertEqualBoms(DecomposedBom expected, DecomposedBom actual) {
        final Map<ReleaseId, ProjectRelease> expectedMap = asReleaseMap(expected);
        final Map<ReleaseId, ProjectRelease> actualMap = asReleaseMap(actual);
        final Iterator<ProjectRelease> expectedReleases = expectedMap.values().iterator();
        while (expectedReleases.hasNext()) {
            final ProjectRelease expectedRelease = expectedReleases.next();
            assertEqualReleases(expectedRelease, actualMap.remove(expectedRelease.id()));

        }
    }

    public static void assertEqualReleases(ProjectRelease expected, ProjectRelease actual) {
        if (expected == null) {
            assertNull(actual);
        }
        if (actual == null) {
            fail("Expected release " + expected.id());
        }
        assertEquals(expected.id(), actual.id());
        assertEquals(asStringSet(expected.dependencies()), asStringSet(actual.dependencies()));
    }

    public static Artifact aetherArtifact(String coordsStr) {
        final AppArtifactCoords c = AppArtifactCoords.fromString(coordsStr);
        return new DefaultArtifact(c.getGroupId(), c.getArtifactId(), c.getClassifier(), c.getType(), c.getVersion());
    }

    private static Set<String> asStringSet(Collection<ProjectDependency> deps) {
        final Set<String> set = new HashSet<>(deps.size());
        for (ProjectDependency d : deps) {
            set.add(d.dependency().toString());
        }
        return set;
    }

    private static Map<ReleaseId, ProjectRelease> asReleaseMap(DecomposedBom bom) {
        final Map<ReleaseId, ProjectRelease> expectedMap = new HashMap<>();
        for (ProjectRelease r : bom.releases()) {
            expectedMap.put(r.id(), r);
        }
        return expectedMap;
    }
}
