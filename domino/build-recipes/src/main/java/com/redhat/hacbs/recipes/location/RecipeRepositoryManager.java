package com.redhat.hacbs.recipes.location;

import com.redhat.hacbs.recipes.util.GitCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;

/**
 * A recipe database stored in git.
 */
public class RecipeRepositoryManager implements RecipeDirectory {
    public static final String SCM_INFO = "scm-info";
    public static final String BUILD_INFO = "build-info";
    public static final String BUILD_TOOL_INFO = "build-tool-info";
    public static final String REPOSITORY_INFO = "repository-info";
    public static final String DISABLED_PLUGINS = "disabled-plugins";
    private final Git git;
    private final String remote;
    private final Path local;
    private final String branch;
    private final Optional<Duration> updateInterval;
    private final RecipeLayoutManager recipeLayoutManager;
    private volatile long lastUpdate = -1;

    public RecipeRepositoryManager(Git git, String remote, Path local, String branch, Optional<Duration> updateInterval) {
        this.git = git;
        this.remote = remote;
        this.local = local;
        this.branch = branch;
        this.updateInterval = updateInterval;
        this.lastUpdate = System.currentTimeMillis();
        this.recipeLayoutManager = new RecipeLayoutManager(local);
    }

    public static RecipeRepositoryManager create(String remote)
            throws GitAPIException, IOException {
        return create(remote, Optional.empty(), Files.createTempDirectory("recipe"));
    }

    public static RecipeRepositoryManager create(String remote, Optional<Duration> updateInterval,
            Path directory) throws GitAPIException, IOException {
        //Allow cloning of another branch via <url>#<branch> format.
        String branch = "main";
        int b = remote.indexOf('#');
        if (b > 0) {
            branch = remote.substring(b + 1);
            remote = remote.substring(0, b);
        }
        return create(remote, branch, updateInterval, directory);

    }

    public static RecipeRepositoryManager create(String remote, String branch, Optional<Duration> updateInterval,
            Path directory) throws GitAPIException {

        var clone = Git.cloneRepository()
                .setBranch(branch)
                .setDirectory(directory.toFile())
                .setCredentialsProvider(new GitCredentials())
                .setDepth(1)
                .setURI(remote);
        var result = clone.call();

        return new RecipeRepositoryManager(result, remote, directory, branch, updateInterval);
    }

    public static RecipeRepositoryManager createLocal(Path directory) throws GitAPIException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(directory + " is not a valid directory");
        }
        try {
            var result = Git.open(directory.toFile());
            return new RecipeRepositoryManager(result, null, directory, result.getRepository().getBranch(), Optional.empty());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open repository " + directory, e);
        }
    }

    /**
     * Returns the directories that contain the recipe information for this specific artifact
     *
     * @param groupId The group id
     * @param artifactId The artifact id
     * @param version The version
     * @return The path match result
     */
    public Optional<RecipePathMatch> getArtifactPaths(String groupId, String artifactId, String version) {
        doUpdate();
        return recipeLayoutManager.getArtifactPaths(groupId, artifactId, version);
    }

    @Override
    public Optional<Path> getBuildPaths(String scmUri, String version) {
        doUpdate();
        return recipeLayoutManager.getBuildPaths(scmUri, version);
    }

    @Override
    public Optional<Path> getRepositoryPaths(String name) {
        doUpdate();
        return recipeLayoutManager.getRepositoryPaths(name);
    }

    @Override
    public List<Path> getAllRepositoryPaths() {
        doUpdate();
        return recipeLayoutManager.getAllRepositoryPaths();
    }

    @Override
    public Optional<Path> getBuildToolInfo(String name) {
        doUpdate();
        return recipeLayoutManager.getBuildToolInfo(name);
    }

    @Override
    public Optional<Path> getDisabledPlugins(String tool) {
        doUpdate();
        return recipeLayoutManager.getDisabledPlugins(tool);
    }

    @Override
    public void update() {
        try {
            git.pull().setContentMergeStrategy(ContentMergeStrategy.THEIRS).setStrategy(MergeStrategy.THEIRS)
                    .call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        lastUpdate = System.currentTimeMillis();
    }

    private void doUpdate() {
        if (updateInterval.isEmpty()) {
            return;
        }
        if (lastUpdate + updateInterval.get().toMillis() < System.currentTimeMillis()) {
            synchronized (this) {
                if (lastUpdate + updateInterval.get().toMillis() < System.currentTimeMillis()) {
                    update();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "RecipeRepositoryManager{" +
                ", remote='" + remote + '\'' +
                ", local=" + local +
                ", branch='" + branch + '\'' +
                ", updateInterval=" + updateInterval +
                ", recipeLayoutManager=" + recipeLayoutManager +
                ", lastUpdate=" + lastUpdate +
                '}';
    }
}
