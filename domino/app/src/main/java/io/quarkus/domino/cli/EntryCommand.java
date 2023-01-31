package io.quarkus.domino.cli;

import io.quarkus.domino.cli.gradle.FromGradle;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true, subcommands = { Report.class, FromMaven.class, FromGradle.class,
        FromConfig.class, Project.class, Version.class })
public class EntryCommand {

}
