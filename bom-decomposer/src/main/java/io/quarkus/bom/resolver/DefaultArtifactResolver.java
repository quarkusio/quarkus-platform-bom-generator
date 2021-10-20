package io.quarkus.bom.resolver;

import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalWorkspace;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * This class wraps the underlying Maven artifact resolver and may keep an artifact info cache, e.g.
 * artifacts that aren't resolvable.
 */
public class DefaultArtifactResolver implements ArtifactResolver {

    private static final String NOT_FOUND_ARTIFACTS = "not-found-artifacts.txt";

    static DefaultArtifactResolver newInstance(MavenArtifactResolver resolver, Path baseDir) {
        return new DefaultArtifactResolver(resolver, baseDir);
    }

    private final MavenArtifactResolver resolver;
    private final Path baseDir;
    private final Path notFoundArtifactsPath;
    private final Set<AppArtifactCoords> notFoundArtifacts = new HashSet<>(0);

    private DefaultArtifactResolver(MavenArtifactResolver resolver, Path baseDir) {
        this.resolver = Objects.requireNonNull(resolver);
        this.baseDir = baseDir;
        if (baseDir != null) {
            final Path cacheDir = baseDir.resolve(".quarkus-bom-generator");
            if (!Files.exists(cacheDir)) {
                try {
                    Files.createDirectories(cacheDir);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create cache directory " + cacheDir, e);
                }
            }
            notFoundArtifactsPath = cacheDir.resolve(NOT_FOUND_ARTIFACTS);
            if (Files.exists(notFoundArtifactsPath)) {
                try (BufferedReader reader = Files.newBufferedReader(notFoundArtifactsPath)) {
                    String s;
                    while ((s = reader.readLine()) != null) {
                        notFoundArtifacts.add(AppArtifactCoords.fromString(s));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read " + notFoundArtifactsPath, e);
                }
            }
        } else {
            notFoundArtifactsPath = null;
        }
    }

    @Override
    public Path getBaseDir() {
        return baseDir;
    }

    @Override
    public ArtifactResult resolve(Artifact a) {
        final AppArtifactCoords coords = toCoords(a);
        if (isRecordedAsNonExisting(coords)) {
            throw recordedAsNonExistingError(coords);
        }
        try {
            return resolver.resolve(a);
        } catch (BootstrapMavenException e) {
            if (isArtifactNotFoundError(e)) {
                persistNotFoundArtifacts(coords);
            }
            throw new ArtifactNotFoundException("Failed to resolve " + a, e);
        }
    }

    @Override
    public ArtifactResult resolveOrNull(Artifact a) {
        final AppArtifactCoords coords = toCoords(a);
        if (isRecordedAsNonExisting(coords)) {
            return null;
        }
        try {
            return resolver.resolve(a);
        } catch (BootstrapMavenException e) {
            if (isArtifactNotFoundError(e)) {
                persistNotFoundArtifacts(coords);
            }
            return null;
        }
    }

    @Override
    public MavenArtifactResolver underlyingResolver() {
        return resolver;
    }

    @Override
    public ArtifactDescriptorResult describe(Artifact a) {
        final AppArtifactCoords coords = toCoords(a);
        if (isRecordedAsNonExisting(coords)) {
            throw recordedAsNonExistingError(coords);
        }
        final ArtifactDescriptorResult result;
        try {
            result = resolver.resolveDescriptor(a);
        } catch (BootstrapMavenException e) {
            throw new ArtifactNotFoundException("Failed to describe " + a, e);
        }
        // if it didn't throw an exception it still might not exist in the local repo
        final boolean pomExists;
        if (a.getFile() == null) {
            if (new File(resolver.getSession().getLocalRepository().getBasedir(),
                    resolver.getSession().getLocalRepositoryManager().getPathForLocalArtifact(a)).exists()) {
                pomExists = true;
            } else {
                final LocalWorkspace workspace = resolver.getMavenContext().getWorkspace();
                pomExists = workspace != null && workspace.getProject(a.getGroupId(), a.getArtifactId()) != null;
            }
        } else {
            pomExists = a.getFile().exists();
        }
        if (!pomExists) {
            persistNotFoundArtifacts(coords);
            throw new ArtifactNotFoundException(
                    a + " was found in neither the Maven local repository nor in the project's workspace");
        }
        return result;
    }

    private void persistNotFoundArtifacts(AppArtifactCoords coords) {
        if (notFoundArtifactsPath == null) {
            return;
        }
        notFoundArtifacts.add(coords);
        try (BufferedWriter writer = Files.newBufferedWriter(notFoundArtifactsPath, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.append(coords.toString());
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist not found artifact list to " + notFoundArtifactsPath, e);
        }
    }

    private boolean isRecordedAsNonExisting(AppArtifactCoords coords) {
        return notFoundArtifacts.contains(coords);
    }

    private static boolean isArtifactNotFoundError(Throwable t) {
        t = t.getCause();
        if (t instanceof ArtifactResolutionException) {
            t = t.getCause();
            if (t instanceof org.eclipse.aether.transfer.ArtifactNotFoundException) {
                return true;
            }
        }
        return false;
    }

    private static AppArtifactCoords toCoords(Artifact a) {
        return new AppArtifactCoords(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private ArtifactNotFoundException recordedAsNonExistingError(final AppArtifactCoords coords) {
        return new ArtifactNotFoundException("Artifact " + coords + " was previously recorded as non-existing");
    }
}
