package io.quarkus.domino.cli;

import java.util.concurrent.Callable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

@CommandLine.Command(name = "version", header = "Display version information.")
public class Version implements Callable<Integer> {

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Override
    public Integer call() throws Exception {
        System.out.println(version);
        return CommandLine.ExitCode.OK;
    }
}
