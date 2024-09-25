package io.quarkus.platform.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.bootstrap.util.PropertyUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * This test generates, builds and tests a sample Quarkus application (unit and integration tests).
 */
public class SampleProjectGenerationTest {

    private static final Logger log = Logger.getLogger(SampleProjectGenerationTest.class);

    private static final String SAMPLE_APP = "sample-app";
    private static final String GENERATE_APP_TIMEOUT = "sample-app.generate-timeout";
    private static final String BUILD_APP_TIMEOUT = "sample-app.build-timeout";

    @Test
    public void testSampleProjectGeneration() throws Exception {
        final Path projectDir = generateSampleAppWithMaven();
        buildApp(projectDir);
    }

    private static void buildApp(Path projectDir) throws IOException {

        final Path cliLog = projectDir.resolve("build.log");
        final Path cliErrors = projectDir.resolve("build-errors.log");
        final long startTime = System.currentTimeMillis();
        Process process = new ProcessBuilder()
                .directory(projectDir.toFile())
                .command("./mvnw", "verify", "-DskipITs=false")
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
                fail("The process has exceeded the time out limit of " + getBuildAppTimeout() + " seconds");
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

    private static String getMvnPath() {
        var mavenHome = System.getProperty("maven.home");
        if (mavenHome == null) {
            return "mvn";
        }
        return Path.of(mavenHome).resolve("bin").resolve(
                PropertyUtils.isWindows() ? "mvn.bat" : "mvn").toString();
    }

    private static Path generateSampleAppWithMaven() throws IOException {
        final String quarkusPlatformGroupId = getRequiredProperty("quarkus.platform.group-id");
        final String quarkusPlatformVersion = getRequiredProperty("quarkus.platform.version");

        final Path workDir = Path.of("").normalize().toAbsolutePath().resolve("target").resolve("sample-app");
        IoUtils.recursiveDelete(workDir);
        Files.createDirectories(workDir);
        final Path cliLog = workDir.resolve("cli.log");
        final Path cliErrors = workDir.resolve("cli-errors.log");
        final long startTime = System.currentTimeMillis();
        Process process = new ProcessBuilder()
                .directory(workDir.toFile())
                .command(getMvnPath(),
                        quarkusPlatformGroupId + ":quarkus-maven-plugin:" + quarkusPlatformVersion + ":create",
                        "-DplatformGroupId=" + quarkusPlatformGroupId,
                        "-DplatformVersion=" + quarkusPlatformVersion,
                        "-DprojectGroupId=org.acme",
                        "-DprojectArtifactId=" + SAMPLE_APP,
                        "-DprojectVersion=1.0-SNAPSHOT",
                        "-DquarkusRegistryClient=false")
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
                fail("The process has exceeded the time out limit of " + getGenerateAppTimeout() + " seconds");
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
}
