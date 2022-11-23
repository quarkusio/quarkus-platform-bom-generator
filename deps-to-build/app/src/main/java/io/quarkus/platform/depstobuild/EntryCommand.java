package io.quarkus.platform.depstobuild;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.platform.depstobuild.gradle.FromGradle;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true, subcommands = { FromMaven.class, FromGradle.class,
        FromConfig.class, Project.class })
public class EntryCommand {

}
