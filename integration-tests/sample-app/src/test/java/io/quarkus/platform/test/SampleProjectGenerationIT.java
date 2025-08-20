package io.quarkus.platform.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.listener.ChainedRepositoryListener;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * This test generates, builds and tests a sample Quarkus application (unit and integration tests).
 */
public class SampleProjectGenerationIT {

    private static final Logger log = Logger.getLogger(SampleProjectGenerationIT.class);

    private static final String SAMPLE_APP = "sample-app";
    private static final String GENERATE_APP_TIMEOUT = "sample-app.generate-timeout";
    private static final String BUILD_APP_TIMEOUT = "sample-app.build-timeout";
    public static final String QUARKUS_PLATFORM_MAVEN_PLUGIN_POM = "quarkus.platform.maven-plugin.pom";

    private static String PLUGIN_GROUP_ID;
    private static String PLUGIN_VERSION;
    private static Path workDir;
    private static Path pluginRepoDir;

    @BeforeAll
    public static void preinstallPlugin() throws Exception {

        PLUGIN_GROUP_ID = getRequiredProperty("quarkus.platform.group-id");
        PLUGIN_VERSION = getRequiredProperty("quarkus.platform.version");

        workDir = Path.of("target/plugin-test").toAbsolutePath();
        recursiveDelete(workDir);
        pluginRepoDir = workDir.resolve("maven-repo");

        installLocalPlugin();
    }

    /**
     * If the target Maven plugin is a part of the project, this method will install the Maven plugin and its relevant
     * dependencies
     * into a temporary local repository to make them resolvable from the test.
     *
     * @throws Exception in case anything goes wrong
     */
    private static void installLocalPlugin() throws Exception {

        BootstrapMavenContext mvnCtx = new BootstrapMavenContext(
                BootstrapMavenContext.config()
                        .setWorkspaceDiscovery(true)
                        .setPreferPomsFromWorkspace(true)
                        .setWorkspaceModuleParentHierarchy(true)
                        .setCurrentProject(System.getProperty(QUARKUS_PLATFORM_MAVEN_PLUGIN_POM)));
        var project = mvnCtx.getCurrentProject();
        if (project == null) {
            return;
        }

        var session = new DefaultRepositorySystemSession(mvnCtx.getRepositorySystemSession());
        session.setCache(null);
        final List<Artifact> artifactsToInstall = new ArrayList<>();

        session.setRepositoryListener(
                ChainedRepositoryListener.newInstance(session.getRepositoryListener(), new AbstractRepositoryListener() {
                    @Override
                    public void artifactResolved(RepositoryEvent event) {
                        var artifact = event.getArtifact();
                        var module = project.getWorkspace().getProject(artifact.getGroupId(), artifact.getArtifactId());
                        if (module != null) {
                            artifactsToInstall.add(artifact);
                            // unfortunately with the workspace reader, the repository doesn't seem to be triggered for the parent hierarchy
                            module = module.getLocalParent();
                            while (module != null) {
                                artifactsToInstall.add(new DefaultArtifact(module.getGroupId(), module.getArtifactId(),
                                        ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, module.getVersion(),
                                        Map.of(), module.getRawModel().getPomFile()));
                                module = module.getLocalParent();
                            }
                        }
                    }
                }));

        mvnCtx.getRepositorySystem().resolveDependencies(session,
                new DependencyRequest().setCollectRequest(
                        new CollectRequest(
                                new Dependency(
                                        new DefaultArtifact(PLUGIN_GROUP_ID, "quarkus-maven-plugin", ArtifactCoords.TYPE_JAR,
                                                PLUGIN_VERSION),
                                        JavaScopes.COMPILE),
                                mvnCtx.getRemoteRepositories())));

        if (!artifactsToInstall.isEmpty()) {
            var installer = MavenArtifactResolver.builder()
                    .setLocalRepository(pluginRepoDir.toString())
                    .setRepositorySystem(mvnCtx.getRepositorySystem())
                    .setRemoteRepositories(mvnCtx.getRemoteRepositories())
                    .setRemoteRepositoryManager(mvnCtx.getRemoteRepositoryManager())
                    .setCurrentProject(project)
                    .build();
            for (var a : artifactsToInstall) {
                installer.install(a);
            }
        }
    }

    @Test
    public void testSampleProjectGeneration() throws Exception {
        // create a sample project
        final Path projectDir = generateSampleAppWithMaven();
        // build and test the sample project
        buildApp(projectDir);
    }

    private static void buildApp(Path projectDir) throws IOException {

        final Path cliLog = projectDir.resolve("build.log");
        final Path cliErrors = projectDir.resolve("build-errors.log");
        final long startTime = System.currentTimeMillis();
        Process process = new ProcessBuilder()
                .directory(projectDir.toFile())
                .command(isWindows() ? "mvnw" : "./mvnw", "verify", "-DskipITs=false",
                        "-Dmaven.repo.local.tail=" + pluginRepoDir)
                .redirectOutput(cliLog.toFile())
                .redirectError(cliErrors.toFile())
                .start();
        log.info("Building the application with '" + process.info().commandLine().orElse("<command not available>") + "'");
        try {
            if (!process.waitFor(getBuildAppTimeout(), TimeUnit.SECONDS)) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    log.error("Failed to destroy the process", e);
                }
                final String timeoutMessage = "The process has exceeded the timeout limit of " + getBuildAppTimeout()
                        + " seconds";
                log.error(timeoutMessage);
                logCliProcessOutput(cliLog, cliErrors);
                fail(timeoutMessage);
            }
        } catch (InterruptedException e) {
            log.error("Interrupted waiting for the process to complete", e);
        }
        process = process.onExit().join();
        log.infof("Done in %.1f seconds", ((double) (System.currentTimeMillis() - startTime)) / 1000);

        if (process.exitValue() != 0) {
            var message = Files.readString(cliErrors);
            if (message.isEmpty()) {
                fail(Files.readString(cliLog));
            }
            fail(Files.readString(cliErrors));
        }
    }

    private static void logCliProcessOutput(Path cliLog, Path cliErrors) throws IOException {
        if (Files.exists(cliLog)) {
            String output = Files.readString(cliLog);
            if (!output.isBlank()) {
                log.info("BEGIN the CLI process output:");
                log.info(output);
                log.info("END the CLI process output");
            }
        }
        if (Files.exists(cliLog)) {
            String output = Files.readString(cliErrors);
            if (!output.isBlank()) {
                log.info("BEGIN the CLI process error output:");
                log.info(output);
                log.info("END the CLI process error output");
            }
        }
    }

    private static String getMvnPath() {
        var mavenHome = System.getProperty("maven.home");
        if (mavenHome == null) {
            return "mvn";
        }
        return Path.of(mavenHome).resolve("bin").resolve(isWindows() ? "mvn.bat" : "mvn").toString();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }

    private static Path generateSampleAppWithMaven() throws IOException {

        Files.createDirectories(workDir);
        final Path cliLog = workDir.resolve("cli.log");
        final Path cliErrors = workDir.resolve("cli-errors.log");
        final long startTime = System.currentTimeMillis();
        Process process = new ProcessBuilder()
                .directory(workDir.toFile())
                .command(getMvnPath(),
                        PLUGIN_GROUP_ID + ":quarkus-maven-plugin:" + PLUGIN_VERSION + ":create",
                        "-DplatformGroupId=" + PLUGIN_GROUP_ID,
                        "-DplatformVersion=" + PLUGIN_VERSION,
                        "-DprojectGroupId=org.acme",
                        "-DprojectArtifactId=" + SAMPLE_APP,
                        "-DprojectVersion=1.0-SNAPSHOT",
                        "-DquarkusRegistryClient=false",
                        "-Dmaven.repo.local.tail=" + pluginRepoDir)
                .redirectOutput(cliLog.toFile())
                .redirectError(cliErrors.toFile())
                .start();
        log.info("Generating application with '" + process.info().commandLine().orElse("<command not available>") + "'");
        try {
            if (!process.waitFor(getGenerateAppTimeout(), TimeUnit.SECONDS)) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    log.error("Failed to destroy the process", e);
                }
                final String timeoutMessage = "The process has exceeded the time out limit of " + getGenerateAppTimeout()
                        + " seconds";
                log.error(timeoutMessage);
                logCliProcessOutput(cliLog, cliErrors);
                fail(timeoutMessage);
            }
        } catch (InterruptedException e) {
            log.error("Interrupted waiting for the process to complete", e);
        }
        process = process.onExit().join();
        log.infof("Done in %.1f seconds", ((double) (System.currentTimeMillis() - startTime)) / 1000);

        if (process.exitValue() != 0) {
            var message = Files.readString(cliErrors);
            if (message.isEmpty()) {
                fail(Files.readString(cliLog));
            }
            fail(Files.readString(cliErrors));
        }
        var projectDir = workDir.resolve(SAMPLE_APP);
        assertThat(projectDir).exists();
        return projectDir;
    }

    private static String getRequiredProperty(String name) {
        var value = System.getProperty(name);
        if (value == null) {
            fail("Required property " + name + " is not set");
        }
        return value;
    }

    private static long getBuildAppTimeout() {
        var propValue = System.getProperty(BUILD_APP_TIMEOUT);
        if (propValue == null) {
            return 30;
        }
        return Long.parseLong(propValue);
    }

    private static long getGenerateAppTimeout() {
        var propValue = System.getProperty(GENERATE_APP_TIMEOUT);
        if (propValue == null) {
            return 10;
        }
        return Long.parseLong(propValue);
    }

    public static void recursiveDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        try {
                            Files.delete(dir);
                        } catch (IOException ex) {
                        }
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (IOException e) {
        }
    }
}
