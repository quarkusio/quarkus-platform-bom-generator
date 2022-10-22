package io.quarkus.bom.decomposer.maven;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * This is a WIP for non-Quarkus-based projects.
 * <p>
 * Logs artifact coordinates (one per line) that represent supported artifacts and their dependencies
 * down to a certain depth level that need to be built from source.
 * <p>
 * The goal exposes other options that enable logging extra information, however all the extra info will be logged
 * with `#` prefix, which the tools parsing the output could treat as a comment and ignore.
 */
@Mojo(name = "deps-to-rebuild", threadSafe = true, requiresProject = false)
public class NonQuarkusDepsToBuildMojo extends AbstractMojo {

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(required = false, defaultValue = "${project.file}")
    File projectFile;

    /**
     * Coordinates of the BOM containing Quarkus extensions. If not provided defaults to the current project's POM
     */
    @Parameter(required = true, property = "bom", defaultValue = "${project.groupId}:${project.artifactId}::pom:${project.version}")
    String bom;

    /**
     * The depth level of a dependency tree of each supported Quarkus extension to capture.
     * Setting the level to 0 will target the supported extension artifacts themselves.
     * Setting the level to 1, will target the supported extension artifacts plus their direct dependencies.
     * If the level is not specified, the default will be -1, which means all the levels.
     */
    @Parameter(required = true, property = "level", defaultValue = "-1")
    int level = -1;

    /**
     * Whether to exclude dependencies (and their transitive dependencies) that aren't managed in the BOM. The default is true.
     */
    @Parameter(required = false, property = "includeNonManaged")
    boolean includeNonManaged;

    /**
     * Whether to log the coordinates of the artifacts captured down to the depth specified. The default is true.
     */
    @Parameter(required = false, property = "logArtifactsToBuild")
    boolean logArtifactsToBuild = true;

    /**
     * Whether to log the module GAVs the artifacts to be built belongs to instead of all
     * the complete artifact coordinates to be built.
     * If this option is enabled, it overrides {@link #logArtifactsToBuild}
     */
    @Parameter(required = false, property = "logModulesToBuild")
    boolean logModulesToBuild;

    /**
     * Whether to log the dependency trees walked down to the depth specified. The default is false.
     */
    @Parameter(required = false, property = "logTrees")
    boolean logTrees;

    /**
     * Whether to log the coordinates of the artifacts below the depth specified. The default is false.
     */
    @Parameter(required = false, property = "logRemaining")
    boolean logRemaining;
    /**
     * Whether to log the summary at the end. The default is true.
     */
    @Parameter(required = false, property = "logSummary")
    boolean logSummary = true;
    /**
     * Whether to log the summary at the end. The default is true.
     */
    @Parameter(required = false, property = "logNonManagedVisited")
    boolean logNonManagedVisited;

    /**
     * If specified, this parameter will cause the output to be written to the path specified, instead of writing to
     * the console.
     */
    @Parameter(property = "outputFile", required = false)
    File outputFile;

    /**
     * Whether to append outputs into the output file or overwrite it.
     */
    @Parameter(property = "appendOutput", required = false, defaultValue = "false")
    boolean appendOutput;

    /*
     * Whether to log code repository info for the artifacts to be built from source
     */
    @Parameter(property = "logCodeRepos", required = false)
    boolean logCodeRepos;

    /*
     * Whether to log code repository dependency graph.
     */
    @Parameter(property = "logCodeRepoGraph", required = false)
    boolean logCodeRepoGraph;

    /*
     * Whether to exclude parent POMs from the list of artifacts to be built from source
     */
    @Parameter(property = "excludeParentPoms", required = false)
    boolean excludeParentPoms;

    /*
     * Whether to exclude BOMs imported in the POMs of artifacts to be built from the list of artifacts to be built from source
     */
    @Parameter(property = "excludeBomImports", required = false)
    boolean excludeBomImports;

    /*
     * Top level artifacts that should be built from source whose dependencies should be analyzed according to the config and
     * the relevant ones should be collected to be built from source too
     */
    @Parameter(required = false)
    List<String> topLevelArtifactsToBuild = List.of();

    /*
     * Artifacts to be excluded
     */
    @Parameter(required = false)
    Set<String> excludeArtifacts = Set.of();

    /*
     * Artifact groupIds to be excluded
     */
    @Parameter(required = false)
    Set<String> excludeGroupIds = Set.of();

    /*
     * Artifact keys to be excluded
     */
    @Parameter(required = false)
    Set<String> excludeKeys = Set.of();

    /*
     * Artifact groupIds to be included
     */
    @Parameter(required = false)
    Set<String> includeGroupIds = Set.of();

    /*
     * Artifact keys to be included
     */
    @Parameter(required = false)
    Set<String> includeKeys = Set.of();

    @Parameter(required = false, property = "validateCodeRepoTags")
    boolean validateCodeRepoTags;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        ArtifactCoords targetBomCoords = ArtifactCoords.fromString(bom);
        if (!ArtifactCoords.TYPE_POM.equals(targetBomCoords.getType())) {
            targetBomCoords = ArtifactCoords.pom(targetBomCoords.getGroupId(), targetBomCoords.getArtifactId(),
                    targetBomCoords.getVersion());
        }

        MavenArtifactResolver resolver;
        try {
            resolver = MavenArtifactResolver.builder()
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setWorkspaceDiscovery(projectFile != null)
                    .setPreferPomsFromWorkspace(projectFile != null)
                    .setCurrentProject(projectFile == null ? null : projectFile.toString())
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }

        DependenciesToBuildReportGenerator.builder()
                .setBom(targetBomCoords)
                .setTopLevelArtifactsToBuild(
                        topLevelArtifactsToBuild.stream().map(ArtifactCoords::fromString).collect(Collectors.toList()))
                .setIncludeGroupIds(includeGroupIds)
                .setIncludeKeys(toKeySet(includeKeys))
                .setExcludeArtifacts(toCoordsSet(excludeArtifacts))
                .setExcludeGroupIds(excludeGroupIds)
                .setExcludeKeys(toKeySet(excludeKeys))
                .setExcludeBomImports(excludeBomImports)
                .setExcludeParentPoms(excludeParentPoms)
                .setIncludeNonManaged(includeNonManaged)
                .setLevel(level)
                .setLogArtifactsToBuild(logArtifactsToBuild)
                .setLogCodeRepoGraph(logCodeRepoGraph)
                .setLogCodeRepos(logCodeRepos)
                .setLogModulesToBuild(logModulesToBuild)
                .setLogNonManagedVisited(logNonManagedVisited)
                .setLogRemaining(logRemaining)
                .setLogSummary(logSummary)
                .setLogTrees(logTrees)
                .setMessageWriter(new MojoMessageWriter(getLog()))
                .setOutputFile(outputFile)
                .setAppendOutput(appendOutput)
                .setValidateCodeRepoTags(validateCodeRepoTags)
                .setResolver(resolver)
                .build().generate();
    }

    private static Set<ArtifactCoords> toCoordsSet(Collection<String> set) {
        final Set<ArtifactCoords> result = new HashSet<>(set.size());
        for (String s : set) {
            result.add(ArtifactCoords.fromString(s));
        }
        return result;
    }

    private static Set<ArtifactKey> toKeySet(Collection<String> set) {
        final Set<ArtifactKey> result = new HashSet<>(set.size());
        for (String s : set) {
            result.add(ArtifactKey.fromString(s));
        }
        return result;
    }
}
