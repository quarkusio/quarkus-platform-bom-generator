package io.quarkus.domino.cli;

import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.manifest.ManifestGenerator;
import picocli.CommandLine;

@CommandLine.Command(name = "report")
public class Report extends BaseDepsToBuildCommand {

    @CommandLine.Option(names = { "--manifest" }, description = "Generate an SBOM", defaultValue = "false")
    public boolean manifest;

    @Override
    protected Integer process(ProjectDependencyResolver depResolver) {
        if (manifest) {
            depResolver.consumeSorted(ManifestGenerator.builder()
                    .setArtifactResolver(getArtifactResolver())
                    .build().toConsumer());
        } else {
            depResolver.log();
        }
        return CommandLine.ExitCode.OK;
    }
}
