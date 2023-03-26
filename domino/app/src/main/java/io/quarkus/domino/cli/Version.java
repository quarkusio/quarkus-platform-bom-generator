package io.quarkus.domino.cli;

import io.quarkus.domino.DominoInfo;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "version", header = "Display version information.")
public class Version implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println(DominoInfo.VERSION);
        return CommandLine.ExitCode.OK;
    }
}
