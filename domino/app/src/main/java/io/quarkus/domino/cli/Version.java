package io.quarkus.domino.cli;

import io.quarkus.domino.DominoVersion;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "version", header = "Display version information.")
public class Version implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println(DominoVersion.VERSION);
        return CommandLine.ExitCode.OK;
    }
}
