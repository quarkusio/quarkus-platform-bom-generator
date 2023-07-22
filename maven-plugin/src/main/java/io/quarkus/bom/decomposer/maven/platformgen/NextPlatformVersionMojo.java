package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.platform.version.PncVersionProvider;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "next-platform-version", threadSafe = true)
public class NextPlatformVersionMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter
    PlatformConfig platformConfig;

    @Parameter(property = "apply")
    boolean apply;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        var releaseConfig = platformConfig == null ? null : platformConfig.getRelease();
        if (releaseConfig == null) {
            getLog().info("Platform release configuration is missing");
            return;
        }

        final String nextVersion;
        if ("PNC".equalsIgnoreCase(releaseConfig.getVersionIncrementor())) {
            nextVersion = PncVersionProvider.getNextRedHatBuildVersion(project.getGroupId(),
                    project.getArtifactId(), project.getVersion());
        } else if (project.getVersion().endsWith("-SNAPSHOT")) {
            nextVersion = project.getVersion().substring(0, project.getVersion().length() - "-SNAPSHOT".length());
        } else {
            var v = new DefaultArtifactVersion(project.getVersion());
            var sb = new StringBuilder();
            sb.append(v.getMajorVersion()).append(".").append(v.getMinorVersion()).append(".")
                    .append(v.getIncrementalVersion() + 1);
            if (v.getQualifier() != null) {
                sb.append(".").append(v.getQualifier());
            }
            nextVersion = sb.toString();
        }

        if (project.getVersion().equals(nextVersion)) {
            getLog().info("The current project version is already set the next release version");
        } else {
            getLog().info("The next project version will be " + nextVersion);
            if (apply) {
                var model = project.getOriginalModel();
                var modelVersion = model.getVersion();
                if (modelVersion == null) {
                    getLog().error("Cannot set the next version since the version is managed by a parent POM");
                    return;
                }
                final Path pomPath = project.getFile().toPath();
                String pomContent;
                try {
                    pomContent = Files.readString(pomPath);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to read " + project.getFile(), e);
                }
                pomContent = pomContent.replaceFirst(project.getVersion(), nextVersion);
                try (BufferedWriter writer = Files.newBufferedWriter(pomPath)) {
                    writer.write(pomContent);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to write the POM to " + project.getFile(), e);
                }
            }
        }
    }
}
