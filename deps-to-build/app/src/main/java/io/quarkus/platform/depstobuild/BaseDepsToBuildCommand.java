package io.quarkus.platform.depstobuild;

import io.quarkus.bom.decomposer.maven.DependenciesToBuildReportGenerator;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;

public class BaseDepsToBuildCommand implements Callable<Integer> {

    @CommandLine.Option(names = {
            "--bom" }, description = "BOM whose constraints should be used as top level artifacts to be built")
    public String bom;

    @CommandLine.Option(names = { "--include-non-managed" }, description = "Include non-managed dependencies")
    public Boolean includeNonManaged;

    @CommandLine.Option(names = {
            "--root-artifacts" }, description = "Root artifacts whose dependencies should be built from source")
    public Collection<String> rootArtifacts = List.of();

    @CommandLine.Option(names = {
            "--level" }, description = "Dependency tree depth level to which the dependencies should be analyzed. If a level is not specified, there is no limit on the level.")
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
            "--log-code-repos" }, description = "Whether to log code repository info for the artifacts to be built from source")
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
            "--validate-code-repo-tags" }, description = "Whether to validate the discovered code repo and tags that are included in the report")
    public boolean validateCodeRepoTags;

    @CommandLine.Option(names = {
            "--warn-on-resolution-errors" }, description = "Whether to warn about artifact resolution errors instead of failing the process")
    public Boolean warnOnResolutionErrors;

    @CommandLine.Option(names = {
            "--include-already-built" }, description = "Whether to include dependencies that have already been built")
    public boolean includeAlreadyBuilt;

    @Override
    public Integer call() throws Exception {
        DependenciesToBuildReportGenerator.Builder builder = DependenciesToBuildReportGenerator.builder();

        if (bom != null) {
            builder.setBom(ArtifactCoords.fromString(bom));
        }

        if (warnOnResolutionErrors != null) {
            builder.setWarnOnResolutionErrors(warnOnResolutionErrors);
        } else if (bom != null) {
            builder.setWarnOnResolutionErrors(true);
        }

        if (includeNonManaged != null) {
            builder.setIncludeNonManaged(includeNonManaged);
        } else if (bom == null) {
            builder.setIncludeNonManaged(true);
        }

        builder.setAppendOutput(appendOutput)
                .setExcludeArtifacts(Set.of()) // TODO
                .setExcludeBomImports(excludeBomImports)
                .setExcludeGroupIds(Set.of()) // TODO
                .setExcludeKeys(Set.of()) // TODO
                .setExcludeParentPoms(excludeParentPoms)
                .setIncludeAlreadyBuilt(includeAlreadyBuilt)
                .setIncludeArtifacts(Set.of()) // TODO
                .setIncludeGroupIds(Set.of()) // TODO
                .setIncludeKeys(Set.of()) // TODO
                .setLevel(level)
                .setLogArtifactsToBuild(logArtifactsToBuild)
                .setLogCodeRepoGraph(logCodeRepoGraph)
                .setLogCodeRepos(logCodeRepos)
                .setLogModulesToBuild(logModulesToBuild)
                .setLogNonManagedVisited(logNonManagedVisited)
                .setLogRemaining(logRemaining)
                .setLogSummary(logSummary)
                .setLogTrees(logTrees)
                .setOutputFile(outputFile)
                .setTopLevelArtifactsToBuild(
                        rootArtifacts.stream().map(ArtifactCoords::fromString).collect(Collectors.toList()))
                .setValidateCodeRepoTags(validateCodeRepoTags)
                .build().generate();

        return CommandLine.ExitCode.OK;
    }
}
