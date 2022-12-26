package io.quarkus.domino.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "project", header = "Project commands", subcommands = {
        ProjectCreate.class
})
public class Project implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return CommandLine.ExitCode.OK;
    }
}
