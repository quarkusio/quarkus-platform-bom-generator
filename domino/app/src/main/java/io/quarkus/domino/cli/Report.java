package io.quarkus.domino.cli;

import io.quarkus.domino.DominoInfo;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.manifest.ManifestGenerator;
import io.quarkus.domino.manifest.SbomGeneratingDependencyVisitor;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(name = "report")
public class Report extends BaseDepsToBuildCommand {

    @CommandLine.Option(names = {
            "--manifest" }, description = "Generate an SBOM with dependency trees", defaultValue = "false")
    public boolean manifest;

    @CommandLine.Option(names = {
            "--flat-manifest" }, description = "Generate an SBOM without dependency tree information", defaultValue = "false")
    public boolean flatManifest;

    @CommandLine.Option(names = {
            "--enable-sbom-transformers" }, description = "Apply SBOM transformers found on the classpath", defaultValue = "false")
    public boolean enableSbomTransformers;

    @Override
    protected Path getConfigDir() {
        if (manifest) {
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
        }
    }

    @Override
    protected void initResolver(ProjectDependencyResolver.Builder resolverBuilder) {
        super.initResolver(resolverBuilder);
        var outputFile = resolverBuilder.getLogOutputFile();
        if (manifest || flatManifest) {
            resolverBuilder.setLogOutputFile(null);
        }
        if (manifest) {
            resolverBuilder.addDependencyTreeVisitor(
                    new SbomGeneratingDependencyVisitor(getArtifactResolver(),
                            outputFile, resolverBuilder.getDependencyConfig().getProductInfo(), enableSbomTransformers));
        }
    }

    @Override
    protected Integer process(ProjectDependencyResolver depResolver) {
        if (manifest) {
            depResolver.resolveDependencies();
        } else if (flatManifest) {
            ManifestGenerator.builder()
                    .setArtifactResolver(getArtifactResolver())
                    .setOutputFile(depResolver.getOutputFile())
                    .build().toConsumer()
                    .accept(depResolver.getReleaseRepos());
        } else {
            depResolver.log();
        }
        return CommandLine.ExitCode.OK;
    }
}
