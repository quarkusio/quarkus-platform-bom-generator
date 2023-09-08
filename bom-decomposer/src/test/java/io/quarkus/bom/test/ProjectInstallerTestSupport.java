package io.quarkus.bom.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

    protected Path workDir() {
        return workDir;
    }

    protected ProjectReleaseInstaller projectWithParentPom(String coords) {
        return ProjectReleaseInstaller.forParentPom(coords).resolver(resolver);
    }

    protected ProjectReleaseInstaller projectWithScmAndTag(String scm, String tag) {
        return ProjectReleaseInstaller.forScmAndTag(scm, tag).resolver(resolver);
    }

    protected PomInstaller pomInstaller(String coords) {
        return PomInstaller.forCoords(coords).resolver(resolver);
    }

    protected BomDecomposer.BomDecomposerConfig bomDecomposer() {
        return BomDecomposer.config().mavenArtifactResolver(ArtifactResolverProvider.get(resolver)).logger(log);
    }

    protected Artifact resolve(String coordsStr) {
        final ArtifactCoords coords = ArtifactCoords.fromString(coordsStr);
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
        final Map<ScmRevision, ProjectRelease> expectedMap = asReleaseMap(expected);
        final Map<ScmRevision, ProjectRelease> actualMap = asReleaseMap(actual);
        for (ProjectRelease expectedRelease : expectedMap.values()) {
            assertEqualReleases(expectedRelease, actualMap.remove(expectedRelease.id()));
        }
        if (!actualMap.isEmpty()) {
            fail("Unexpected releases: " + actualMap.keySet());
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
        final ArtifactCoords c = ArtifactCoords.fromString(coordsStr);
        return new DefaultArtifact(c.getGroupId(), c.getArtifactId(), c.getClassifier(), c.getType(), c.getVersion());
    }

    private static Set<String> asStringSet(Collection<ProjectDependency> deps) {
        final Set<String> set = new HashSet<>(deps.size());
        for (ProjectDependency d : deps) {
            set.add(d.dependency().toString());
        }
        return set;
    }

    private static Map<ScmRevision, ProjectRelease> asReleaseMap(DecomposedBom bom) {
        final Map<ScmRevision, ProjectRelease> expectedMap = new HashMap<>();
        for (ProjectRelease r : bom.releases()) {
            expectedMap.put(r.id(), r);
        }
        return expectedMap;
    }
}
