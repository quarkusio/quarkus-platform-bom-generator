package io.quarkus.domino.cli;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;

public abstract class BaseDepsToBuildCommand implements Callable<Integer> {

    @CommandLine.Option(names = { "--project-dir" }, description = "Project directory")
    public File projectDir;

    @CommandLine.Option(names = {
            "--bom" }, description = "BOM whose constraints should be used as top level artifacts to be built")
    public String bom;

    @CommandLine.Option(names = { "--include-non-managed" }, description = "Include non-managed dependencies")
    public Boolean includeNonManaged;

    @CommandLine.Option(names = {
            "--root-artifacts" }, description = "Root artifacts whose dependencies should be built from source", split = ",")
    public Collection<String> rootArtifacts = List.of();

    @CommandLine.Option(names = {
            "--level" }, description = "Dependency tree depth level to which the dependencies should be analyzed. If a level is not specified, there is no limit on the level.", defaultValue = "-1")
    public int level = -1;

    @CommandLine.Option(names = {
            "--log-artifacts-to-build" }, description = "Whether to log the coordinates of the artifacts captured down to the depth specified. The default is true.")
    public boolean logArtifactsToBuild = true;

    @CommandLine.Option(names = {
            "--log-modules-to-build" }, description = "Whether to log the module GAVs the artifacts to be built belongs to instead of all the complete artifact coordinates to be built. If this option is enabled, it overrides {@link #logArtifactsToBuild}")
    public boolean logModulesToBuild;

    @CommandLine.Option(names = {
            "--log-trees" }, description = "Whether to log the dependency trees walked down to the depth specified. The default is false.")
    public boolean logTrees;

    @CommandLine.Option(names = {
            "--log-remaining" }, description = "Whether to log the coordinates of the artifacts below the depth specified. The default is false.")
    public boolean logRemaining;

    @CommandLine.Option(names = {
            "--log-summary" }, description = "Whether to log the summary at the end. The default is true.")
    public boolean logSummary = true;

    @CommandLine.Option(names = {
            "--log-non-managed-visited" }, description = "Whether to log the summary at the end. The default is true.")
    public boolean logNonManagedVisited;

    @CommandLine.Option(names = {
            "--output-file" }, description = "If specified, this parameter will cause the output to be written to the path specified, instead of writing to the console.")
    public File outputFile;

    @CommandLine.Option(names = {
            "--append-output" }, description = "Whether to append outputs into the output file or overwrite it.")
    public boolean appendOutput;

    @CommandLine.Option(names = {
            "--log-code-repos" }, description = "Whether to log code repository info for the artifacts to be built from source", defaultValue = "true")
    public boolean logCodeRepos = true;

    @CommandLine.Option(names = {
            "--log-code-repo-graph" }, description = "Whether to log code repository dependency graph.")
    public boolean logCodeRepoGraph;

    @CommandLine.Option(names = {
            "--exclude-parent-poms" }, description = "Whether to exclude parent POMs from the list of artifacts to be built from source")
    public boolean excludeParentPoms;

    @CommandLine.Option(names = {
            "--exclude-bom-imports" }, description = "Whether to exclude BOMs imported in the POMs of artifacts to be built from the list of artifacts to be built from source")
    public boolean excludeBomImports;

    @CommandLine.Option(names = {
            "--exclude-group-ids" }, description = "Command-separated list of groupIds of dependencies that should be excluded")
    public String excludeGroupIds;

    @CommandLine.Option(names = {
            "--exclude-keys" }, description = "Command-separated list of artifact coordinates excluding the version of dependencies that should be excluded")
    public String excludeKeys;

    @CommandLine.Option(names = {
            "--include-group-ids" }, description = "Command-separated list of groupIds of dependencies that should be included")
    public String includeGroupIds;

    @CommandLine.Option(names = {
            "--recipe-repos" }, description = "Build recipe repository URLs, the default one is https://github.com/redhat-appstudio/jvm-build-data")
    public String recipeRepos;

    @CommandLine.Option(names = {
            "--validate-code-repo-tags" }, description = "Whether to validate the discovered code repo and tags that are included in the report")
    public boolean validateCodeRepoTags;

    @CommandLine.Option(names = { "--legacy-scm-locator" }, description = "Whether to use the legacy SCM locator")
    public boolean legacyScmLocator;

    @CommandLine.Option(names = {
            "--warn-on-resolution-errors" }, description = "Whether to warn about artifact resolution errors instead of failing the process")
    public Boolean warnOnResolutionErrors;

    @CommandLine.Option(names = {
            "--warn-on-missing-scm" }, description = "Whether to issue a warning in case an SCM location could not be determined or fail with an error (the default behavior).")
    public boolean warnOnMissingScm;

    @CommandLine.Option(names = {
            "--include-already-built" }, description = "Whether to include dependencies that have already been built")
    public boolean includeAlreadyBuilt;

    @CommandLine.Option(names = {
            "--export-config-to" }, description = "Export config to a file")
    public File exportTo;

    @CommandLine.Option(names = {
            "--include-optional-deps" }, description = "Includes optional dependencies of the root project artifacts")
    public boolean includeOptionalDeps;

    @CommandLine.Option(names = {
            "--gradle-java8" }, description = "Whether to use Java 8 (configured with JAVA8_HOME) to fetch dependency information from a Gradle project")
    public boolean gradleJava8;

    @CommandLine.Option(names = {
            "--gradle-java-home" }, description = "Java home directory to use for fetching dependency information from a Gradle project")
    public String gradleJavaHome;

    @CommandLine.Option(names = {
            "--maven-profiles",
            "-P" }, description = "Comma-separated list of Maven profiles that should be enabled when analyzing dependencies")
    public String mavenProfiles;

    @CommandLine.Option(names = { "--maven-settings", "-s" }, description = "Path to a custom Maven settings file")
    public String mavenSettings;

    private MavenArtifactResolver artifactResolver;

    @Override
    public Integer call() throws Exception {

        final ProjectDependencyConfig.Mutable config = ProjectDependencyConfig.builder();
        initConfig(config);

        if (exportTo != null) {
            config.persist(exportTo.toPath());
        } else {
            final ProjectDependencyResolver dependencyResolver = ProjectDependencyResolver.builder()
                    .setLogOutputFile(outputFile == null ? null : outputFile.toPath())
                    .setAppendOutput(appendOutput)
                    .setDependencyConfig(config)
                    .setArtifactResolver(getArtifactResolver())
                    .build();
            return process(dependencyResolver);
        }
        return CommandLine.ExitCode.OK;
    }

    protected void initConfig(ProjectDependencyConfig.Mutable config) {
        if (bom != null) {
            config.setProjectBom(ArtifactCoords.fromString(bom));
        }

        if (warnOnResolutionErrors != null) {
            config.setWarnOnResolutionErrors(warnOnResolutionErrors);
        } else if (bom != null) {
            config.setWarnOnResolutionErrors(true);
        }

        if (projectDir != null) {
            if (!projectDir.isDirectory()) {
                throw new RuntimeException(projectDir + " is not a directory");
            }
            config.setProjectDir(projectDir.toPath());
        }

        if (includeNonManaged != null) {
            config.setIncludeNonManaged(includeNonManaged);
        } else if (bom == null || projectDir != null) {
            config.setIncludeNonManaged(true);
        }

        final Set<String> excludeGroupIds;
        if (this.excludeGroupIds != null) {
            excludeGroupIds = Set.of(this.excludeGroupIds.split(","));
        } else {
            excludeGroupIds = Set.of();
        }

        final Set<ArtifactKey> excludeKeys;
        if (this.excludeKeys != null) {
            final String[] keyStrs = this.excludeKeys.split(",");
            excludeKeys = new HashSet<>(keyStrs.length);
            for (String keyStr : keyStrs) {
                final String[] parts = keyStr.split(":");
                excludeKeys.add(ArtifactKey.of(parts[0],
                        parts.length > 1 ? parts[1] : "*",
                        parts.length > 2 ? parts[2] : "*",
                        parts.length > 3 ? parts[3] : "*"));
            }
        } else {
            excludeKeys = Set.of();
        }

        if (recipeRepos != null) {
            final String[] arr = recipeRepos.split(",");
            final List<String> list = new ArrayList<>(arr.length);
            for (String s : arr) {
                if (!s.isBlank()) {
                    list.add(s.trim());
                }
            }
            config.setRecipeRepos(list);
        }

        config.setExcludeBomImports(excludeBomImports)
                .setExcludeGroupIds(excludeGroupIds) // TODO
                .setExcludeKeys(excludeKeys)
                .setExcludeParentPoms(excludeParentPoms)
                .setIncludeArtifacts(Set.of()) // TODO
                .setIncludeGroupIds(Set.of()) // TODO
                .setIncludeKeys(Set.of()) // TODO
                .setLevel(level)
                .setLogArtifactsToBuild(logArtifactsToBuild)
                .setLogCodeRepoTree(logCodeRepoGraph)
                .setLogCodeRepos(logCodeRepos)
                .setLogModulesToBuild(logModulesToBuild)
                .setLogNonManagedVisited(logNonManagedVisited)
                .setLogRemaining(logRemaining)
                .setLogSummary(logSummary)
                .setLogTrees(logTrees)
                .setProjectArtifacts(
                        rootArtifacts.stream().map(ArtifactCoords::fromString).collect(Collectors.toList()))
                .setValidateCodeRepoTags(validateCodeRepoTags)
                .setLegacyScmLocator(legacyScmLocator)
                .setIncludeAlreadyBuilt(includeAlreadyBuilt)
                .setIncludeOptionalDeps(includeOptionalDeps)
                .setGradleJava8(gradleJava8)
                .setGradleJavaHome(gradleJavaHome)
                .setWarnOnMissingScm(warnOnMissingScm);
    }

    protected MavenArtifactResolver getArtifactResolver() {
        if (artifactResolver != null) {
            return artifactResolver;
        }

        StringBuilder sb = null;
        if (mavenProfiles != null) {
            sb = new StringBuilder().append("-P").append(mavenProfiles);
        }
        if (mavenSettings != null) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            sb.append(" -s ").append(mavenSettings);
        }
        if (sb != null) {
            System.setProperty(BootstrapMavenOptions.QUARKUS_INTERNAL_MAVEN_CMD_LINE_ARGS, sb.toString());
        }

        try {
            if (projectDir == null) {
                return artifactResolver = MavenArtifactResolver.builder().setWorkspaceDiscovery(false).build();
            }
            return MavenArtifactResolver.builder()
                    .setCurrentProject(projectDir.getAbsolutePath())
                    .setEffectiveModelBuilder(true)
                    .setPreferPomsFromWorkspace(true)
                    .build();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
        }
    }

    protected abstract Integer process(ProjectDependencyResolver depResolver);
}
