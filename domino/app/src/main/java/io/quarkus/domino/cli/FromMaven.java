package io.quarkus.domino.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "from-maven", header = "Project commands", subcommands = {
        Report.class, Build.class
})
public class FromMaven implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return CommandLine.ExitCode.OK;
    }
}
