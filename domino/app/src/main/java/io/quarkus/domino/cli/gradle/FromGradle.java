package io.quarkus.domino.cli.gradle;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.cli.BaseDepsToBuildCommand;
import io.quarkus.domino.cli.ToolConfig;
import io.quarkus.domino.gradle.GradleActionOutcome;
import io.quarkus.domino.gradle.PublicationReader;
import io.quarkus.maven.dependency.ArtifactCoords;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.jgit.api.Git;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.gradle.GradlePublication;
import picocli.CommandLine;

@CommandLine.Command(name = "from-gradle")
public class FromGradle extends BaseDepsToBuildCommand {

    private static final boolean RESOLVE_MODULE_METADATA = false;
    private static final boolean JDK8 = false;

    @Inject
    ToolConfig toolConfig;

    @CommandLine.Option(names = { "--project" }, description = "Project name", required = true)
    public String projectName;

    @CommandLine.Option(names = { "--tag" }, description = "Release tag", required = true)
    public String tag;

    @CommandLine.Option(names = { "--java-home" }, description = "Java home directory to use to execute Gradle project tasks")
    public String javaHome;

    @Override
    public Integer call() throws Exception {

        final Path projectDir;
        if (projectName != null && !projectName.isBlank()) {
            projectDir = toolConfig.getConfigDir().resolve(projectName).resolve("git");
            if (!Files.exists(projectDir)) {
                System.out.println("Project " + projectName + " is not found");
                return CommandLine.ExitCode.USAGE;
            }
        } else {
            System.out.println("Project name was not provided");
            return CommandLine.ExitCode.USAGE;
        }

        //final PublishedProject project = getPublishedProject("kafka", "3.2.3");
        //final PublishedProject project = getPublishedProject("micrometer", "1.9.5");
        //final PublishedProject project = getPublishedProject("opentelemetry-java", "1.19.0");
        //final PublishedProject project = getPublishedProject("opentelemetry-java-instrumentation", "1.9.2-alpha");
        //final PublishedProject project = getPublishedProject("grpc-java", "1.50.2");
        //final PublishedProject project = getPublishedProject("graphql-java", "19.2.0");
        final PublishedProject project = getPublishedProject(projectDir, tag);
        /* @formatter:off
        System.out.println("Published artifacts:");
        if (project.getBoms().isEmpty()) {
            log("  BOMs: none");
        } else {
            log("  BOMs:");
            for (ArtifactCoords c : project.getBoms()) {
                log("    " + c.toCompactCoords());
            }
        }
        if (project.getLibraries().isEmpty()) {
            log("  Libraries: none");
        } else {
            log("  Libraries:");
            for (ArtifactCoords c : project.getLibraries()) {
                log("    " + c.toCompactCoords());
            }
        }
@formatter:on */

        if (this.rootArtifacts.isEmpty()) {
            this.rootArtifacts = project.getLibraries().stream().map(ArtifactCoords::toString).collect(Collectors.toList());
        } else {
            final List<String> tmp = new ArrayList<>(rootArtifacts.size() + project.getLibraries().size());
            tmp.addAll(this.rootArtifacts);
            project.getLibraries().stream().map(ArtifactCoords::toString).forEach(this.rootArtifacts::add);
            this.rootArtifacts = tmp;
        }

        return super.call();
    }

    private PublishedProject getPublishedProject(Path projectDir, String version) {
        return toPublishedProject(readPublishedArtifacts(projectDir, version), version);
    }

    private PublishedProject toPublishedProject(List<GradleModuleVersion> moduleVersions, String version) {
        MavenArtifactResolver resolver = null;
        if (RESOLVE_MODULE_METADATA) {
            log("Resolving metadata of published modules");
            resolver = getArtifactResolver();
        }
        final PublishedProject publishedProject = new PublishedProject();
        for (GradleModuleVersion m : moduleVersions) {
            if (resolver != null) {
                final Path moduleJson;
                try {
                    moduleJson = resolver.resolve(toModuleArtifact(m)).getArtifact().getFile().toPath();
                } catch (BootstrapMavenException e) {
                    log("WARN: failed to resolve " + toModuleArtifact(m));
                    continue;
                }
                final GradleModuleMetadata metadata = GradleModuleMetadata.deserialize(moduleJson);
                if (metadata.isBom()) {
                    publishedProject.addBom(
                            ArtifactCoords.pom(metadata.getGroupId(), metadata.getArtifactId(),
                                    alignVersion(metadata.getVersion(), version)));
                } else {
                    publishedProject.addLibrary(
                            ArtifactCoords.jar(metadata.getGroupId(), metadata.getArtifactId(),
                                    alignVersion(metadata.getVersion(), version)));
                }
            } else {
                if (m.getName().endsWith("-bom") || m.getName().startsWith("bom-") || m.getName().contains("-bom-")) {
                    publishedProject
                            .addBom(ArtifactCoords.pom(m.getGroup(), m.getName(), alignVersion(m.getVersion(), version)));
                } else {
                    publishedProject
                            .addLibrary(ArtifactCoords.jar(m.getGroup(), m.getName(), alignVersion(m.getVersion(), version)));
                }
            }
        }
        return publishedProject;
    }

    private static String alignVersion(String v, String version) {
        return !v.equals(version) && isSnapshot(v) ? version : v;
    }

    private static Artifact toModuleArtifact(GradleModuleVersion module) {
        return new DefaultArtifact(module.getGroup(), module.getName(), ArtifactCoords.DEFAULT_CLASSIFIER, "module",
                module.getVersion());
    }

    private List<GradleModuleVersion> readPublishedArtifacts(Path projectDir, String tag) {

        System.out.println("Checking out tag " + tag);
        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName(tag).call();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open Git repository at " + projectDir, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to checkout tag " + tag, e);
        }

        log("Connecting to " + projectDir);
        final ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir.toFile())
                //.useGradleVersion(gradleVersion)
                .connect();
        log("Resolving module publications");
        try {
            final GradleActionOutcome<List<GradlePublication>> outcome = new GradleActionOutcome<>();
            final BuildActionExecuter<List<GradlePublication>> actionExecuter = connection
                    .action(new PublicationReader()).withArguments("-PskipAndroid=true").setStandardOutput(System.out);
            if (javaHome != null && !javaHome.isBlank()) {
                actionExecuter.setJavaHome(new File(javaHome));
            } else if (JDK8) {
                actionExecuter.setJavaHome(new File("/home/aloubyansky/jdk/jdk1.8.0_261"));
            }
            actionExecuter.run(outcome);
            return outcome.getResult().stream().map(GradlePublication::getId).collect(Collectors.toList());
        } finally {
            connection.close();
        }
    }

    private static boolean isSnapshot(String v) {
        return v.endsWith("-SNAPSHOT");
    }

    private static void log(Object s) {
        System.out.println(s == null ? "null" : s);
    }

    @Override
    protected Integer process(ProjectDependencyResolver depResolver) {
        depResolver.log();
        return CommandLine.ExitCode.OK;
    }
}
