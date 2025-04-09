package io.quarkus.domino.cli;

import io.quarkus.domino.DominoInfo;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.manifest.SbomGeneratingDependencyVisitor;
import io.quarkus.domino.manifest.SbomGenerator;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(name = "report")
public class Report extends BaseDepsToBuildCommand {

    private static final String MANIFEST_DEPS_NONE = "none";
    private static final String MANIFEST_DEPS_TREE = "tree";
    private static final String MANIFEST_DEPS_GRAPH = "graph";

    @CommandLine.Option(names = {
            "--manifest" }, description = "Generate an SBOM with dependency trees", defaultValue = "false")
    public boolean manifest;

    @CommandLine.Option(names = {
            "--cdx-schema-version" }, description = "CycloneDX spec version. Can be used only with the --manifest argument. Defaults to the latest supported by the integrated CycloneDX library.")
    public String cdxSchemaVersion;

    @CommandLine.Option(names = {
            "--flat-manifest" }, description = "Generate an SBOM without dependency tree information", defaultValue = "false")
    public boolean flatManifest;

    @CommandLine.Option(names = {
            "--manifest-dependencies" }, description = "Strategy to manifest dependencies: none, tree (the default, based on the default conflict free dependency trees returned by the Maven resolver), graph (records all direct dependencies of each artifact)", defaultValue = MANIFEST_DEPS_GRAPH)
    public String manifestDependencies;

    @CommandLine.Option(names = {
            "--enable-sbom-transformers" }, description = "Apply SBOM transformers found on the classpath", defaultValue = "false")
    public boolean enableSbomTransformers;

    @CommandLine.Option(names = {
            "--hashes" }, description = "Whether to calculate hashes for manifested components", defaultValue = "true")
    public boolean hashes;

    @Override
    protected Path getConfigDir() {
        if (manifest || flatManifest) {
            return (projectDir == null ? Path.of(DominoInfo.CONFIG_DIR_NAME)
                    : projectDir.toPath().resolve(DominoInfo.CONFIG_DIR_NAME))
                            .resolve("manifest");
        }
        return null;
    }

    @Override
    protected void initConfig(ProjectDependencyConfig.Mutable config) {
        super.initConfig(config);
        if (manifest || flatManifest) {
            config.setIncludeAlreadyBuilt(true);
            if (excludeParentPoms == null) {
                config.setExcludeParentPoms(true);
            }
            if (!flatManifest) {
                config.setVerboseGraphs(MANIFEST_DEPS_GRAPH.equals(manifestDependencies));
            }
        }
    }

    @Override
    protected void initResolver(ProjectDependencyResolver.Builder resolverBuilder) {
        super.initResolver(resolverBuilder);
        var outputFile = resolverBuilder.getLogOutputFile();
        if (manifest || flatManifest) {
            resolverBuilder.setLogOutputFile(null)
                    .addDependencyTreeVisitor(
                            new SbomGeneratingDependencyVisitor(
                                    SbomGenerator.builder()
                                            .setArtifactResolver(getArtifactResolver())
                                            .setOutputFile(outputFile)
                                            .setProductInfo(resolverBuilder.getDependencyConfig().getProductInfo())
                                            .setEnableTransformers(enableSbomTransformers)
                                            .setRecordDependencies(
                                                    !(flatManifest || MANIFEST_DEPS_NONE.equals(manifestDependencies)))
                                            .setCalculateHashes(hashes)
                                            .setSchemaVersion(cdxSchemaVersion),
                                    resolverBuilder.getDependencyConfig()));
        }
    }

    @Override
    protected Integer process(ProjectDependencyResolver depResolver) {
        if (manifest || flatManifest) {
            depResolver.resolveDependencies();
        } else {
            depResolver.log();
        }
        return CommandLine.ExitCode.OK;
    }
}
