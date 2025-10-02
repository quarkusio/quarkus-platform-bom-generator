package io.quarkus.domino.recipes.location;

import io.quarkus.domino.recipes.BuildRecipe;
import io.quarkus.domino.recipes.GAV;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Entry point for requesting build information
 */
public class RecipeGroupManager {

    private static final Logger log = Logger.getLogger(RecipeGroupManager.class.getName());

    /**
     * The repositories, the highest priority first
     */
    private final List<RecipeDirectory> repositories;

    public RecipeGroupManager(List<RecipeDirectory> repositories) {
        this.repositories = repositories;
    }

    public List<Path> lookupScmInformation(GAV gav) {

        List<Path> artifactVersionResults = new ArrayList<>();
        List<Path> artifactResults = new ArrayList<>();
        List<Path> versionResults = new ArrayList<>();
        List<Path> groupResults = new ArrayList<>();

        var group = gav.getGroupId();
        log.infof("Looking up %s", group);

        List<RecipePathMatch> paths = new ArrayList<>();
        //we need to do a lookup
        for (var r : repositories) {
            var possible = r.getArtifactPaths(gav.getGroupId(), gav.getArtifactId(),
                    gav.getVersion());
            if (possible.isPresent()) {
                paths.add(possible.get());
            }
        }

        for (var path : paths) {
            if (path.getArtifactAndVersion() != null) {
                //if there is a file specific to this group, artifact and version it takes priority
                Path resolvedPath = path.getArtifactAndVersion().resolve(BuildRecipe.SCM.getName());
                log.infof("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    artifactVersionResults.add(resolvedPath);
                }
            }
            if (path.getArtifact() != null) {
                Path resolvedPath = path.getArtifact().resolve(BuildRecipe.SCM.getName());
                log.infof("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    artifactResults.add(resolvedPath);
                }
            }
            if (path.getVersion() != null) {
                Path resolvedPath = path.getVersion().resolve(BuildRecipe.SCM.getName());
                log.infof("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    versionResults.add(resolvedPath);
                }
            }
            if (path.getGroup() != null) {
                Path resolvedPath = path.getGroup().resolve(BuildRecipe.SCM.getName());
                log.infof("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    groupResults.add(resolvedPath);
                }
            }
        }
        if (!artifactVersionResults.isEmpty()) {
            return artifactVersionResults;
        }
        if (!artifactResults.isEmpty()) {
            return artifactResults;
        }
        if (!versionResults.isEmpty()) {
            return versionResults;
        }
        return groupResults;
    }

    public BuildInfoResponse requestBuildInformation(BuildInfoRequest buildInfoRequest) {

        String scmUri = normalizeScmUri(buildInfoRequest.getScmUri());

        List<Path> paths = new ArrayList<>();
        for (var r : repositories) {
            var possible = r.getBuildPaths(scmUri, buildInfoRequest.getVersion());
            if (possible.isPresent()) {
                paths.add(possible.get());
            }
        }

        Map<BuildRecipe, Path> buildResults = new HashMap<>();
        for (var recipe : buildInfoRequest.getRecipeFiles()) {
            for (var path : paths) {
                var option = path.resolve(recipe.getName());
                if (Files.exists(option)) {
                    buildResults.put(recipe, option);
                    break;
                }
            }

        }
        return new BuildInfoResponse(buildResults);
    }

    public static String normalizeScmUri(String scmUri) {
        // Remove any fragment
        int pos = scmUri.indexOf("#");
        if (pos != -1) {
            scmUri = scmUri.substring(0, pos);
        }
        if (scmUri.endsWith(".git")) {
            scmUri = scmUri.substring(0, scmUri.length() - 4);
        }
        pos = scmUri.indexOf("://");
        if (pos != -1) {
            scmUri = scmUri.substring(pos + 3);
        }
        return scmUri;
    }

    public void forceUpdate() {
        for (var r : repositories) {
            r.update();
        }
    }
}
