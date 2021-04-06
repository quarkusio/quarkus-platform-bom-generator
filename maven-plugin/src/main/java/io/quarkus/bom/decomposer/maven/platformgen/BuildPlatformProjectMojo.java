package io.quarkus.bom.decomposer.maven.platformgen;

import java.io.File;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

@Mojo(name = "invoke-platform-project", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyCollection = ResolutionScope.NONE)
public class BuildPlatformProjectMojo extends AbstractMojo {

    @Parameter(required = true, defaultValue = "${basedir}/generated-platform-project")
    File outputDir;

    @Parameter(defaultValue = "${maven.home}")
    String mavenHome;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession mavenSession;

    @Component
    private Invoker invoker;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setProperties(mavenSession.getRequest().getUserProperties());
        request.setPomFile(new File(outputDir, "pom.xml"));
        request.setGoals(mavenSession.getRequest().getGoals());
        request.setShowErrors(mavenSession.getRequest().isShowErrors());
        request.setShellEnvironmentInherited(true);
        request.setBatchMode(true);

        request.setProfiles(mavenSession.getRequest().getActiveProfiles());
        request.setProperties(mavenSession.getRequest().getUserProperties());

        final InvocationResult result;
        try {
            result = invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed to build the platform project", e);
        }
        if (result.getExitCode() != 0) {
            if (result.getExecutionException() != null) {
                throw new MojoExecutionException("Failed to build the platform project", result.getExecutionException());
            }
            throw new MojoExecutionException("Failed to build the platform project, please consult the errors logged above.");
        }
    }
}
