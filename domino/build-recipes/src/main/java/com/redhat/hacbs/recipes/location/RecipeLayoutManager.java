package com.redhat.hacbs.recipes.location;

import com.redhat.hacbs.recipes.build.AddBuildRecipeRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Manages an individual recipe database of build recipes.
 * <p>
 * Layout is specified as:
 * <p>
 * /recipes/io/quarkus/ //location information for everything in the io/quarkus group
 * /recipes/io/quarkus/security/ //info for the io.quarkus.security group
 * /recipes/io/quarkus/_artifact/quarkus-core/ //artifact level information for quarkus-core (hopefully not common)
 * /recipes/io/quarkus/_version/2.2.0-rhosk3/ //location information for version 2.2.0-rhosk3
 * /recipes/io/quarkus/_artifact/quarkus-core/_version/2.2.0-rhosk3/ //artifact level information for a specific version of
 * quarkus core
 * <p>
 * Different pieces of information are stored in different files in these directories specified above, and it is possible
 * to only override some parts of the recipe (e.g. a different location for a service specific version, but everything else is
 * the same)
 * <p>
 * At present this is just the location information.
 */
public class RecipeLayoutManager implements RecipeDirectory {

    private static final Logger log = Logger.getLogger(RecipeLayoutManager.class.getName());

    public static final String ARTIFACT = "_artifact";
    public static final String VERSION = "_version";
    private final Path scmInfoDirectory;
    private final Path buildInfoDirectory;
    private final Path repositoryInfoDirectory;
    private final Path buildToolInfoDirectory;
    private final Path pluginInfoDirectory;

    public RecipeLayoutManager(Path baseDirectory) {
        scmInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.SCM_INFO);
        buildInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.BUILD_INFO);
        repositoryInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.REPOSITORY_INFO);
        buildToolInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.BUILD_TOOL_INFO);
        pluginInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.DISABLED_PLUGINS);
    }

    /**
     * Returns the directories that contain the recipe information for this specific artifact
     */
    public Optional<RecipePathMatch> getArtifactPaths(String groupId, String artifactId, String version) {
        Path groupPath = scmInfoDirectory.resolve(groupId.replace('.', File.separatorChar));
        Path artifactFolder = groupPath.resolve(ARTIFACT);
        Path artifactPath = artifactFolder.resolve(artifactId);
        Path artifactAndVersionPath = null;
        log.warning("Searching for recipe in " + groupPath);

        if (Files.notExists(groupPath)) {
            return Optional.empty();
        }
        boolean groupAuthoritative = true;
        if (Files.exists(artifactPath)) {
            artifactAndVersionPath = resolveVersion(artifactPath, version).orElse(null);
            groupAuthoritative = false;
        } else {
            artifactPath = null;
        }
        Path versionPath = resolveVersion(groupPath, version).orElse(null);
        if (versionPath != null) {
            groupAuthoritative = false;
        }

        return Optional
                .of(new RecipePathMatch(groupPath, artifactPath, versionPath, artifactAndVersionPath, groupAuthoritative));
    }

    @Override
    public Optional<Path> getBuildPaths(String scmUri, String version) {
        Path target = buildInfoDirectory.resolve(RecipeGroupManager.normalizeScmUri(scmUri));
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(resolveVersion(target, version).orElse(target));
    }

    @Override
    public Optional<Path> getRepositoryPaths(String name) {
        Path target = repositoryInfoDirectory.resolve(name + ".yaml");
        if (Files.exists(target)) {
            return Optional.of(target);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Path> getBuildToolInfo(String name) {
        Path target = buildToolInfoDirectory.resolve(name).resolve("tool.yaml");
        if (Files.exists(target)) {
            return Optional.of(target);
        }
        return Optional.empty();
    }

    @Override
    public List<Path> getAllRepositoryPaths() {
        if (Files.notExists(repositoryInfoDirectory)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(repositoryInfoDirectory)) {
            return list.filter(s -> s.toString().endsWith(".yaml")).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Path> getDisabledPlugins(String tool) {
        Path target = pluginInfoDirectory.resolve(tool + ".yaml");
        return Files.isReadable(target) ? Optional.of(target) : Optional.empty();
    }

    private Optional<Path> resolveVersion(Path target, String version) {
        Path versions = target.resolve(VERSION);
        if (!Files.exists(versions)) {
            return Optional.empty();
        }
        ComparableVersion requestedVersion = new ComparableVersion(version);
        ComparableVersion currentVersion = null;
        Path currentPath = null;
        try (var s = Files.list(versions)) {
            var i = s.iterator();
            while (i.hasNext()) {
                var path = i.next();
                ComparableVersion pv = new ComparableVersion(path.getFileName().toString());
                if (requestedVersion.compareTo(pv) <= 0) {
                    if (currentVersion == null || pv.compareTo(currentVersion) < 0) {
                        currentVersion = pv;
                        currentPath = path;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Optional.ofNullable(currentPath);
    }

    @Override
    public <T> void writeBuildData(AddBuildRecipeRequest<T> data) {
        Path target = buildInfoDirectory.resolve(RecipeGroupManager.normalizeScmUri(data.getScmUri()));
        if (data.getVersion() != null) {
            target = target.resolve(VERSION).resolve(data.getVersion());
        }
        try {
            Files.createDirectories(target);
            data.getRecipe().getHandler().write(data.getData(), target.resolve(data.getRecipe().getName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update() {

    }

    @Override
    public <T> void writeArtifactData(AddRecipeRequest<T> data) {
        String groupId = data.getGroupId();
        String artifactId = data.getArtifactId();
        String version = data.getVersion();
        Path resolved = scmInfoDirectory.resolve(groupId.replace('.', File.separatorChar));
        if (artifactId != null) {
            resolved = resolved.resolve(ARTIFACT);
            resolved = resolved.resolve(artifactId);
        }
        if (version != null) {
            resolved = resolved.resolve(VERSION);
            resolved = resolved.resolve(version);
        }
        try {
            Files.createDirectories(resolved);
            data.getRecipe().getHandler().write(data.getData(), resolved.resolve(data.getRecipe().getName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
