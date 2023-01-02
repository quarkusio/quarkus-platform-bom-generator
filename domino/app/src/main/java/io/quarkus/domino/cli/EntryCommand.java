package io.quarkus.domino.cli;

import io.quarkus.domino.cli.gradle.FromGradle;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true, subcommands = { FromMaven.class, FromGradle.class,
        FromConfig.class, Project.class })
public class EntryCommand {

}
