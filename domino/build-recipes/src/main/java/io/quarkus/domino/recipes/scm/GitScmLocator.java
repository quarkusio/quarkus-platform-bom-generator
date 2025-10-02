package io.quarkus.domino.recipes.scm;

import io.quarkus.domino.recipes.BuildRecipe;
import io.quarkus.domino.recipes.GAV;
import io.quarkus.domino.recipes.location.RecipeDirectory;
import io.quarkus.domino.recipes.location.RecipeGroupManager;
import io.quarkus.domino.recipes.location.RecipeRepositoryManager;
import io.quarkus.domino.recipes.util.GitCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.jboss.logging.Logger;

public class GitScmLocator implements ScmLocator {

    private static final Logger log = Logger.getLogger(GitScmLocator.class);

    private static final Pattern NUMERIC_PART = Pattern.compile("(\\d+)(\\.\\d+)+");

    public static GitScmLocator getInstance() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public RecipeGroupManager recipeGroupManager;
        private List<String> recipeRepos = List.of(BuildRecipe.DEFAULT_RECIPE_REPO_URL);
        private boolean cacheRepoTags;
        private String cacheUrl;
        private ScmLocator fallbackScmLocator;
        private boolean cloneLocalRecipeRepos = true;
        private Path gitCloneBaseDir;

        private Builder() {
        }

        /**
         * Base directory for cloning recipe repositories. If not provided,
         * each repository will be cloned in a temporary directory.
         *
         * @param gitCloneBaseDir base directory for cloning recipe repositories
         * @return this builder instance
         */
        public Builder setGitCloneBaseDir(Path gitCloneBaseDir) {
            this.gitCloneBaseDir = gitCloneBaseDir;
            return this;
        }

        /**
         * A list of build recipe repo URIs
         *
         * @param recipeRepos recipe repo URIs
         * @return this builder instance
         */
        public Builder setRecipeRepos(List<String> recipeRepos) {
            if (recipeRepos != null && !recipeRepos.isEmpty()) {
                this.recipeRepos = recipeRepos;
            }
            return this;
        }

        /**
         * Whether to cache code repository tags between {@link ScmLocator#resolveTagInfo(GAV)} calls
         *
         * @param cacheRepoTags whether to cache code repository tags
         * @return this builder instance
         */
        public Builder setCacheRepoTags(boolean cacheRepoTags) {
            this.cacheRepoTags = cacheRepoTags;
            return this;
        }

        /**
         * An SCM locator that should be used in case no information was found in the configured recipe repositories.
         *
         * @param fallbackScmLocator SCM locator that should be used in case no information was found in the configured recipe
         *        repositories
         * @return this builder instance
         */
        public Builder setFallback(ScmLocator fallbackScmLocator) {
            this.fallbackScmLocator = fallbackScmLocator;
            return this;
        }

        /**
         * By default all the configured recipe repositories are cloned into a temporary
         * directory whether they are remote or or available locally.
         * If a local repository is cloned before it is opened then the uncommitted changes present
         * in the original local repository won't be visible to the recipe repository manager.
         *
         * @param cloneLocalRecipeRepos whether to clone local recipe repositories when initializing recipe repository managers
         * @return this builder instance
         */
        public Builder setCloneLocalRecipeRepos(boolean cloneLocalRecipeRepos) {
            this.cloneLocalRecipeRepos = cloneLocalRecipeRepos;
            return this;
        }

        /**
         * Explicitly use an existing {@link RecipeRepositoryManager}. If this is set then other repository settings will have
         * no effect.
         *
         * @param recipeGroupManager The manager
         * @return this builder instance
         */
        public Builder setRecipeGroupManager(RecipeGroupManager recipeGroupManager) {
            this.recipeGroupManager = recipeGroupManager;
            return this;
        }

        public GitScmLocator build() {
            return new GitScmLocator(this);
        }
    }

    private final List<String> recipeRepos;
    private final boolean cacheRepoTags;
    private final ScmLocator fallbackScmLocator;
    private final Map<String, Map<String, String>> repoTagsToHash;
    private final boolean cloneLocalRecipeRepos;
    private final Path gitCloneBaseDir;

    private RecipeGroupManager recipeGroupManager;

    private GitScmLocator(Builder builder) {
        this.recipeRepos = builder.recipeRepos;
        this.cacheRepoTags = builder.cacheRepoTags;
        this.fallbackScmLocator = builder.fallbackScmLocator;
        this.repoTagsToHash = cacheRepoTags ? new HashMap<>() : Map.of();
        this.cloneLocalRecipeRepos = builder.cloneLocalRecipeRepos;
        this.recipeGroupManager = builder.recipeGroupManager;
        this.gitCloneBaseDir = builder.gitCloneBaseDir;
    }

    private RecipeGroupManager getRecipeGroupManager() {
        if (recipeGroupManager == null) {
            final Pattern remotePattern = cloneLocalRecipeRepos ? null : Pattern.compile("(?!file\\b)\\w+?:\\/\\/.*");
            //checkout the git recipe database and load the recipes
            final List<RecipeDirectory> managers = new ArrayList<>(recipeRepos.size());
            for (var i : recipeRepos) {
                final RecipeRepositoryManager repoManager;
                if (remotePattern == null || remotePattern.matcher(i).matches()) {
                    log.infof("Cloning recipe repo %s", i);
                    try {
                        repoManager = gitCloneBaseDir == null
                                ? RecipeRepositoryManager.create(i)
                                : RecipeRepositoryManager.create(i, Optional.empty(),
                                        Files.createTempDirectory(gitCloneBaseDir, "recipe"));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to checkout " + i, e);
                    }
                } else {
                    log.infof("Opening local recipe repo %s", i);
                    final Path p;
                    if (i.startsWith("file:")) {
                        p = Path.of(i.substring("file:".length()));
                    } else {
                        p = Path.of(i);
                    }
                    try {
                        repoManager = RecipeRepositoryManager.createLocal(p);
                    } catch (GitAPIException e) {
                        throw new RuntimeException("Failed to initialize recipe manager from local repository " + i, e);
                    }
                }
                managers.add(repoManager);
            }
            recipeGroupManager = new RecipeGroupManager(managers);
        }
        return recipeGroupManager;
    }

    public TagInfo resolveTagInfo(GAV toBuild) {

        log.debugf("Looking up %s", toBuild);

        var recipeGroupManager = getRecipeGroupManager();

        //look for SCM info
        var recipes = recipeGroupManager
                .lookupScmInformation(toBuild);
        log.infof("Found the following build info files for %s: %s", toBuild, recipes);

        List<RepositoryInfo> repos = new ArrayList<>();
        List<TagMapping> allMappings = new ArrayList<>();
        for (var recipe : recipes) {
            ScmInfo main;
            try {
                main = BuildRecipe.SCM.getHandler().parse(recipe);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to parse " + recipe, e);
            }
            repos.add(main);
            allMappings.addAll(main.getTagMapping());
            for (var j : main.getLegacyRepos()) {
                repos.add(j);
                allMappings.addAll(j.getTagMapping());
            }
        }

        TagInfo fallbackTagInfo = null;
        if (repos.isEmpty()) {
            log.infof("No SCM information found for %s, attempting to use the pom to determine the location", toBuild);
            //TODO: do we want to rely on pom discovery long term? Should we just use this to update the database instead?
            if (fallbackScmLocator != null) {
                fallbackTagInfo = fallbackScmLocator.resolveTagInfo(toBuild);
                if (fallbackTagInfo != null) {
                    repos = List.of(fallbackTagInfo.getRepoInfo());
                }
            }
            if (repos.isEmpty()) {
                throw new RuntimeException("Unable to determine SCM repo");
            }
        }

        RuntimeException firstFailure = null;
        for (var parsedInfo : repos) {
            log.debugf("Looking for a tag in %s", parsedInfo.getUri());

            //now look for a tag
            try {
                final Map<String, String> tagsToHash = getTagToHashMap(parsedInfo);
                if (fallbackTagInfo != null && fallbackTagInfo.getTag() != null) {
                    var hash = tagsToHash.get(fallbackTagInfo.getTag());
                    if (hash != null) {
                        return new TagInfo(fallbackTagInfo.getRepoInfo(), fallbackTagInfo.getTag(), hash);
                    }
                }

                String version = toBuild.getVersion();
                String underscoreVersion = version.replace(".", "_");
                String selectedTag = null;

                //first try tag mappings
                for (var mapping : allMappings) {
                    log.debugf("Trying tag pattern %s on version %s", mapping.getPattern(), version);
                    Matcher m = Pattern.compile(mapping.getPattern()).matcher(version);
                    if (m.matches()) {
                        log.debugf("Tag pattern %s matches", mapping.getPattern());
                        String match = mapping.getTag();
                        for (int i = 0; i <= m.groupCount(); ++i) {
                            match = match.replaceAll("\\$" + i, m.group(i));
                        }
                        log.debugf("Trying to find tag %s", match);
                        //if the tag was a constant we don't require it to be in the tag set
                        //this allows for explicit refs to be used
                        if (tagsToHash.containsKey(match) || match.equals(mapping.getTag())) {
                            selectedTag = match;
                            break;
                        }
                    }
                }

                if (selectedTag == null) {
                    try {
                        selectedTag = runTagHeuristic(version, tagsToHash);
                    } catch (RuntimeException e) {
                        if (firstFailure == null) {
                            firstFailure = e;
                        } else {
                            firstFailure.addSuppressed(e);
                        }
                        //it is a very common pattern to use underscores instead of dots in the tags
                        selectedTag = runTagHeuristic(underscoreVersion, tagsToHash);
                    }
                }

                if (selectedTag != null) {
                    firstFailure = null;
                    String hash = tagsToHash.get(selectedTag);
                    if (hash == null) {
                        hash = selectedTag; //sometimes the tag is a hash
                    }

                    TagInfo result = new TagInfo(parsedInfo, selectedTag, hash);
                    log.infof("Returning tag information of %s", result);
                    return result;
                }
            } catch (RuntimeException ex) {
                log.error("Failure to determine tag", ex);
                if (firstFailure == null) {
                    firstFailure = new RuntimeException("Failed to determine tag for repo " + parsedInfo.getUri(), ex);
                } else {
                    firstFailure.addSuppressed(ex);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }

        return null;
    }

    static String runTagHeuristic(String version, Map<String, String> tagsToHash) {
        String selectedTag = null;
        Set<String> versionExactContains = new HashSet<>();
        Set<String> tagExactContains = new HashSet<>();
        for (var name : tagsToHash.keySet()) {
            if (name.equals(version)) {
                //exact match is always good
                selectedTag = version;
                break;
            } else if (name.contains(version)) {
                versionExactContains.add(name);
            } else if (version.contains(name)) {
                tagExactContains.add(name);
            }
        }
        if (selectedTag != null) {
            return selectedTag;
        }

        //no exact match
        if (versionExactContains.size() == 1) {
            //only one contained the full version
            selectedTag = versionExactContains.iterator().next();
        } else {
            for (var i : versionExactContains) {
                //look for a tag that ends with the version (i.e. no -rc1 or similar)
                if (i.endsWith(version)) {
                    if (selectedTag == null) {
                        selectedTag = i;
                    } else {
                        throw new RuntimeException(
                                "Could not determine tag for " + version
                                        + " multiple possible tags were found: "
                                        + versionExactContains);
                    }
                }
            }
            if (selectedTag == null && tagExactContains.size() == 1) {
                //this is for cases where the tag is something like 1.2.3 and the version is 1.2.3.Final
                //we need to be careful though, as e.g. this could also make '1.2' match '1.2.3'
                //we make sure the numeric part is an exact match
                var tempTag = tagExactContains.iterator().next();
                Matcher tm = NUMERIC_PART.matcher(tempTag);
                Matcher vm = NUMERIC_PART.matcher(version);
                if (tm.find() && vm.find()) {
                    if (Objects.equals(tm.group(0), vm.group(0))) {
                        selectedTag = tempTag;
                    }
                }
            }
            if (selectedTag == null) {
                RuntimeException runtimeException = new RuntimeException(
                        "Could not determine tag for " + version);
                runtimeException.setStackTrace(new StackTraceElement[0]);
                throw runtimeException;
            }
        }
        return selectedTag;
    }

    private Map<String, String> getTagToHashMap(RepositoryInfo repo) {
        Map<String, String> tagsToHash = repoTagsToHash.get(repo.getUri());
        if (tagsToHash == null) {
            tagsToHash = getTagToHashMapFromGit(repo);
            if (cacheRepoTags) {
                repoTagsToHash.put(repo.getUri(), tagsToHash);
            }
        }
        return tagsToHash;
    }

    private static Map<String, String> getTagToHashMapFromGit(RepositoryInfo parsedInfo) {
        Map<String, String> tagsToHash;
        final Collection<Ref> tags;
        try {
            tags = Git.lsRemoteRepository()
                    .setCredentialsProvider(
                            new GitCredentials())
                    .setRemote(parsedInfo.getUriWithoutFragment()).setTags(true).setHeads(false).call();
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to obtain a list of tags from " + parsedInfo.getUri(), e);
        }
        tagsToHash = new HashMap<>(tags.size());
        for (var tag : tags) {
            var name = tag.getName().replace("refs/tags/", "");
            tagsToHash.put(name, tag.getPeeledObjectId() == null ? tag.getObjectId().name() : tag.getPeeledObjectId().name());
        }

        return tagsToHash;
    }
}
