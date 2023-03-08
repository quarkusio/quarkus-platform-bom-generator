package io.quarkus.domino.cli;

import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.manifest.ManifestGenerator;
import io.quarkus.domino.manifest.SbomGeneratingDependencyVisitor;
import picocli.CommandLine;

@CommandLine.Command(name = "report")
public class Report extends BaseDepsToBuildCommand {

    @CommandLine.Option(names = {
            "--manifest" }, description = "Generate an SBOM with dependency trees", defaultValue = "false")
    public boolean manifest;

    @CommandLine.Option(names = {
            "--flat-manifest" }, description = "Generate an SBOM without dependency tree information", defaultValue = "false")
    public boolean flatManifest;

    @Override
    protected void initConfig(ProjectDependencyConfig.Mutable config) {
        super.initConfig(config);
        if (manifest) {
            config.setIncludeAlreadyBuilt(true);
        }
    }

    @Override
    protected void initResolver(ProjectDependencyResolver.Builder resolverBuilder) {
        super.initResolver(resolverBuilder);
        if (manifest || flatManifest) {
            resolverBuilder.setLogOutputFile(null);
        }
        if (manifest) {
            resolverBuilder.addDependencyTreeVisitor(
                    new SbomGeneratingDependencyVisitor(getArtifactResolver(),
                            outputFile == null ? null : outputFile.toPath()));
        }
    }

    @Override
    protected Integer process(ProjectDependencyResolver depResolver) {
        if (manifest) {
            depResolver.resolveDependencies();
        } else if (flatManifest) {
            ManifestGenerator.builder()
                    .setArtifactResolver(getArtifactResolver())
                    .setOutputFile(outputFile == null ? null : this.outputFile.toPath())
                    .build().toConsumer()
                    .accept(depResolver.getReleaseRepos());
        } else {
            depResolver.log();
        }
        return CommandLine.ExitCode.OK;
    }
}
