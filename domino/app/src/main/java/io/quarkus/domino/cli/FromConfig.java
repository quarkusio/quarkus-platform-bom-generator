package io.quarkus.domino.cli;

import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "from-config")
public class FromConfig implements Callable<Integer> {

    @CommandLine.Option(paramLabel = "CONFIG", names = {
            "--config" }, description = "Project dependency configuration.", required = true)
    public File configFile;

    @CommandLine.Option(order = 1, names = {
            "--output-file" }, description = "If specified, this parameter will cause the output to be written to the path specified, instead of writing to the console.")
    public File outputFile;

    @CommandLine.Option(order = 2, names = {
            "--append-output" }, description = "Whether to append outputs into the output file or overwrite it.")
    public boolean appendOutput;

    @Override
    public Integer call() throws Exception {

        ProjectDependencyResolver.builder()
                .setLogOutputFile(outputFile == null ? null : outputFile.toPath())
                .setAppendOutput(appendOutput)
                .setDependencyConfig(ProjectDependencyConfig.fromFile(configFile.toPath()))
                .build().log();

        return CommandLine.ExitCode.OK;
    }
}
