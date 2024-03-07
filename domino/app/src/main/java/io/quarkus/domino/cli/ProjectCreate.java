package io.quarkus.domino.cli;

import jakarta.inject.Inject;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.eclipse.jgit.api.Git;
import picocli.CommandLine;

@CommandLine.Command(name = "create", header = "Create new project")
public class ProjectCreate implements Callable<Integer> {

    @Inject
    ToolConfig toolConfig;

    @CommandLine.Option(paramLabel = "NAME", names = { "--name" }, description = "Name of the project.")
    String name;

    @CommandLine.Option(paramLabel = "REPO-URL", names = { "--repo-url" }, description = "Git repository URL.")
    String repoUrl;

    @Override
    public Integer call() throws Exception {

        final URL repoUrl = this.repoUrl == null || this.repoUrl.isBlank() ? null : new URL(this.repoUrl);
        if (name == null || name.isBlank()) {
            if (repoUrl == null) {
                System.out.println("At least --name or --repo-url is required");
                return CommandLine.ExitCode.USAGE;
            }
            name = repoUrl.getPath();
            final int i = name.lastIndexOf('/');
            if (i >= 0) {
                name = name.substring(i + 1);
            }
            if (name.isBlank()) {
                System.out.println("Failed to derive project name from " + repoUrl);
                return CommandLine.ExitCode.SOFTWARE;
            }
        }

        final Path projectDir = toolConfig.getConfigDir().resolve(name);
        if (Files.exists(projectDir)) {
            System.out.println(
                    "Choose a different name for a project, please. '" + name + "' is already used for something else");
            return CommandLine.ExitCode.USAGE;
        }

        Files.createDirectories(projectDir);
        System.out.println("Created project " + name);

        if (repoUrl != null) {
            System.out.println("Cloning " + repoUrl);
            try (Git git = Git.cloneRepository().setDirectory(projectDir.resolve("git").toFile())
                    .setURI(repoUrl.toExternalForm()).call()) {
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
