package io.quarkus.domino.test.repo;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class TestArtifactRepo {

    public static TestArtifactRepo of(Path dir) {
        return new TestArtifactRepo(dir);
    }

    private final Path dir;

    private TestArtifactRepo(Path dir) {
        this.dir = Objects.requireNonNull(dir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public MavenArtifactResolver getArtifactResolver() {
        try {
            return MavenArtifactResolver.builder()
                    .setOffline(true)
                    .setWorkspaceDiscovery(false)
                    .setLocalRepository(dir.normalize().toAbsolutePath().toString())
                    .build();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException(e);
        }
    }

    public TestArtifactRepo install(TestProject project) {
        if (project.getMainModule() == null) {
            throw new IllegalArgumentException("Project doesn't include any module");
        }
        install(project.getMainModule());
        return this;
    }

    private void install(TestModule module) {
        var artifactDir = dir.resolve(module.getGroupId().replace('.', File.separatorChar))
                .resolve(module.getArtifactId())
                .resolve(module.getVersion());
        try {
            Files.createDirectories(artifactDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (var a : module.getPublishedArtifacts()) {
            if (ArtifactCoords.TYPE_JAR.equals(a.getType())) {
                var name = new StringBuilder();
                name.append(module.getArtifactId()).append("-").append(module.getVersion());
                if (!a.getClassifier().isEmpty()) {
                    name.append("-").append(a.getClassifier());
                }
                name.append(".").append(ArtifactCoords.TYPE_JAR);
                try (var fs = ZipUtils.newZip(artifactDir.resolve(name.toString()))) {
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (ArtifactCoords.TYPE_POM.equals(a.getType())) {
                try {
                    ModelUtils.persistModel(
                            artifactDir.resolve(
                                    module.getArtifactId() + "-" + module.getVersion() + "." + ArtifactCoords.TYPE_POM),
                            module.getModel());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new IllegalStateException("Unsupported artifact type " + a);
            }
        }
        for (TestModule m : module.getModules()) {
            install(m);
        }
    }
}
