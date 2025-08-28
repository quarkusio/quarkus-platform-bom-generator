package io.quarkus.domino.cli;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
            "--include-patterns" }, description = "Artifact inclusion patterns", split = ",")
    public Collection<String> includePatterns = List.of();

    @CommandLine.Option(names = {
            "--level" }, description = "Dependency tree depth level to which the dependencies should be analyzed. If a level is not specified, there is no limit on the level.", defaultValue = "-1")
    public Integer level = -1;

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
            "--log-trees-for" }, description = "Comma-separate list of artifacts to log dependency trees for")
    public String logTreesFor;

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
    public Boolean excludeParentPoms;

    @CommandLine.Option(names = {
            "--exclude-bom-imports" }, description = "Whether to exclude BOMs imported in the POMs of artifacts to be built from the list of artifacts to be built from source")
    public Boolean excludeBomImports;

    @CommandLine.Option(names = {
            "--exclude-group-ids" }, description = "Command-separated list of groupIds of dependencies that should be excluded", split = ",")
    public Set<String> excludeGroupIds;

    @CommandLine.Option(names = {
            "--exclude-keys" }, description = "Command-separated list of artifact coordinates excluding the version of dependencies that should be excluded")
    public String excludeKeys;

    @CommandLine.Option(names = {
            "--include-group-ids" }, description = "Command-separated list of groupIds of dependencies that should be included", split = ",")
    public Set<String> includeGroupIds;

    @CommandLine.Option(names = {
            "--exclude-scopes" }, description = "Command-separated list of dependency scopes that should be excluded when collecting dependencies of the root project artifacts", split = ",")
    public Set<String> excludeScopes;

    @CommandLine.Option(names = {
            "--recipe-repos" }, description = "Build recipe repository URLs, the default one is https://github.com/redhat-appstudio/jvm-build-data", split = ",")
    public List<String> recipeRepos;

    @CommandLine.Option(names = { "--legacy-scm-locator" }, description = "Whether to use the legacy SCM locator")
    public Boolean legacyScmLocator;

    @CommandLine.Option(names = {
            "--warn-on-resolution-errors" }, description = "Whether to warn about artifact resolution errors instead of failing the process")
    public Boolean warnOnResolutionErrors;

    @CommandLine.Option(names = {
            "--warn-on-missing-scm" }, description = "Whether to issue a warning in case an SCM location could not be determined or fail with an error (the default behavior).")
    public Boolean warnOnMissingScm;

    @CommandLine.Option(names = {
            "--include-already-built" }, description = "Whether to include dependencies that have already been built")
    public Boolean includeAlreadyBuilt;

    @CommandLine.Option(names = {
            "--export-config-to" }, description = "Export config to a file")
    public File exportTo;

    @CommandLine.Option(names = {
            "--include-optional-deps" }, description = "Includes optional dependencies of the root project artifacts")
    public Boolean includeOptionalDeps;

    @CommandLine.Option(names = {
            "--gradle-java8" }, description = "Whether to use Java 8 (configured with JAVA8_HOME) to fetch dependency information from a Gradle project")
    public Boolean gradleJava8;

    @CommandLine.Option(names = {
            "--gradle-java-home" }, description = "Java home directory to use for fetching dependency information from a Gradle project")
    public String gradleJavaHome;

    @CommandLine.Option(names = {
            "--maven-profiles",
            "-P" }, description = "Comma-separated list of Maven profiles that should be enabled when analyzing dependencies")
    public String mavenProfiles;

    @CommandLine.Option(names = { "--maven-settings", "-s" }, description = "Path to a custom Maven settings file")
    public String mavenSettings;

    @CommandLine.Option(names = { "--config-file" }, description = "Path to a configuration file to use")
    public File configFile;

    private MavenArtifactResolver artifactResolver;

    @Override
    public Integer call() throws Exception {

        Map<String, ProjectDependencyConfig> configs = Map.of();
        var configDir = getConfigDir();
        if (configDir != null && Files.exists(configDir)) {
            configs = new HashMap<>();
            try (Stream<Path> stream = Files.list(configDir)) {
                var i = stream.iterator();
                while (i.hasNext()) {
                    var p = i.next();
                    var configName = p.getFileName().toString();
                    if (configName.endsWith(".json") && !Files.isDirectory(p)) {
                        configName = configName.substring(0, configName.length() - ".json".length());
                        if (configName.endsWith("-config")) {
                            configName = configName.substring(0, configName.length() - "config".length() - 1);
                        }
                        var config = ProjectDependencyConfig.mutableFromFile(p);
                        initConfig(config);
                        configs.put(configName + "-", config);
                    }
                }
            }
        }
        var log = MessageWriter.info();
        if (configFile != null) {
            if (!configFile.exists()) {
                log.error("Configuration file " + configFile + " does not exist");
                return CommandLine.ExitCode.USAGE;
            }
            var config = ProjectDependencyConfig.mutableFromFile(configFile.toPath());
            initConfig(config);
            configs = Map.of("", config);
        }
        if (configs.isEmpty()) {
            final ProjectDependencyConfig.Mutable config = ProjectDependencyConfig.builder();
            initConfig(config);
            configs = Map.of("", config.build());
        }

        for (var configEntry : configs.entrySet()) {
            var config = configEntry.getValue();
            if (exportTo != null) {
                config.persist(exportTo.toPath());
            } else {
                Path targetFile = null;
                if (outputFile != null) {
                    targetFile = outputFile.toPath();
                    if (!configEntry.getKey().isEmpty()) {
                        var targetFileName = configEntry.getKey() + targetFile.getFileName();
                        var targetDir = targetFile.getParent();
                        targetFile = targetDir == null ? Path.of(targetFileName) : targetDir.resolve(targetFileName);
                        log.info("Generating " + targetFile);
                    }
                }
                final ProjectDependencyResolver.Builder resolverBuilder = ProjectDependencyResolver.builder()
                        .setLogOutputFile(targetFile)
                        .setAppendOutput(appendOutput)
                        .setDependencyConfig(config)
                        .setArtifactResolver(getArtifactResolver())
                        .setMessageWriter(log);
                initResolver(resolverBuilder);
                var exitCode = process(resolverBuilder.build());
                if (exitCode != CommandLine.ExitCode.OK) {
                    return exitCode;
                }
            }
        }
        return CommandLine.ExitCode.OK;
    }

    protected Path getConfigDir() {
        return null;
    }

    protected void initResolver(ProjectDependencyResolver.Builder resolverBuilder) {
    }

    protected void initConfig(ProjectDependencyConfig.Mutable config) {
        if (bom != null) {
            config.setProjectBom(ArtifactCoords.fromString(bom));
        }

        if (projectDir != null) {
            if (!projectDir.isDirectory()) {
                throw new RuntimeException(projectDir + " is not a directory");
            }
            config.setProjectDir(projectDir.toPath().normalize().toAbsolutePath());
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
            config.setRecipeRepos(recipeRepos);
        }

        if (excludeScopes != null) {
            if (excludeScopes.size() == 1 && excludeScopes.contains("none")) {
                excludeScopes = Set.of();
            }
            config.setExcludeScopes(excludeScopes);
        }

        if (warnOnResolutionErrors != null) {
            config.setWarnOnResolutionErrors(warnOnResolutionErrors);
        }
        if (includeNonManaged != null) {
            config.setIncludeNonManaged(includeNonManaged);
        }
        if (excludeBomImports != null) {
            config.setExcludeBomImports(excludeBomImports);
        }
        if (level != null) {
            config.setLevel(level);
        }
        if (legacyScmLocator != null) {
            config.setLegacyScmLocator(legacyScmLocator);
        }
        if (includeAlreadyBuilt != null) {
            config.setIncludeAlreadyBuilt(includeAlreadyBuilt);
        }
        if (includeOptionalDeps != null) {
            config.setIncludeOptionalDeps(includeOptionalDeps);
        }
        if (gradleJava8 != null) {
            config.setGradleJava8(gradleJava8);
        }
        if (warnOnMissingScm != null) {
            config.setWarnOnMissingScm(warnOnMissingScm);
        }
        if (!rootArtifacts.isEmpty()) {
            config.setProjectArtifacts(rootArtifacts.stream().map(ArtifactCoords::fromString).collect(Collectors.toList()));
        }

        config.setExcludeGroupIds(excludeGroupIds == null ? Set.of() : excludeGroupIds)
                .setExcludeKeys(excludeKeys)
                .setExcludeParentPoms(excludeParentPoms != null && excludeParentPoms)
                .setIncludeGroupIds(includeGroupIds == null ? Set.of() : includeGroupIds)
                .setIncludeKeys(Set.of()) // TODO
                .setIncludePatterns(toArtifactCoordsList(includePatterns))
                .setLogArtifactsToBuild(logArtifactsToBuild)
                .setLogTreesFor(logTreesFor)
                .setLogCodeRepoTree(logCodeRepoGraph)
                .setLogCodeRepos(logCodeRepos)
                .setLogModulesToBuild(logModulesToBuild)
                .setLogNonManagedVisited(logNonManagedVisited)
                .setLogRemaining(logRemaining)
                .setLogSummary(logSummary)
                .setLogTrees(logTrees)
                .setGradleJavaHome(gradleJavaHome);
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
                    .setCurrentProject(projectDir.getCanonicalPath())
                    .setEffectiveModelBuilder(true)
                    .setPreferPomsFromWorkspace(true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
        }
    }

    protected abstract Integer process(ProjectDependencyResolver depResolver);

    private static List<ArtifactCoords> toArtifactCoordsList(Collection<String> strList) {
        if (strList == null || strList.isEmpty()) {
            return List.of();
        }
        final List<ArtifactCoords> result = new ArrayList<>(strList.size());
        for (var s : strList) {
            result.add(ArtifactCoords.fromString(s));
        }
        return result;
    }
}
