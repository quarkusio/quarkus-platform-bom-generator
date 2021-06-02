package io.quarkus.bom.decomposer.maven.platformgen;

import java.io.File;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest.ReactorFailureBehavior;
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

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter
    boolean skip;

    @Component
    private Invoker invoker;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skip) {
            getLog().info("Skipping generated platform project invoker");
            return;
        }

        final InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(outputDir, "pom.xml"));
        request.setGoals(mavenSession.getRequest().getGoals());
        request.setShowErrors(mavenSession.getRequest().isShowErrors());
        request.setShellEnvironmentInherited(true);
        request.setBatchMode(true);
        request.setLocalRepositoryDirectory(mavenSession.getRequest().getLocalRepositoryPath());

        final String reactorFailureBehavior = mavenSession.getReactorFailureBehavior();
        if (reactorFailureBehavior != null) {
            request.setReactorFailureBehavior(
                    ReactorFailureBehavior.valueOfByLongOption(reactorFailureBehavior.toLowerCase().replace('_', '-')));
        }

        request.setProfiles(mavenSession.getRequest().getActiveProfiles());
        request.setProperties(mavenSession.getRequest().getUserProperties());

        final String registryUrl = project.getProperties().getProperty(PlatformBomLifecycleParticipant.QUARKUS_REGISTRY_URL);
        final Properties props = request.getProperties();
        if (registryUrl != null && !props.containsKey(PlatformBomLifecycleParticipant.QUARKUS_REGISTRY_URL)) {
            props.setProperty(PlatformBomLifecycleParticipant.QUARKUS_REGISTRY_URL, registryUrl);
        }

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
