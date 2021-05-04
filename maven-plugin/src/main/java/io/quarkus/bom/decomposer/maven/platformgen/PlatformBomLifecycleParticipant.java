package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.registry.Constants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "quarkus-platform-bom-lifecycle")
public class PlatformBomLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    static final String QUARKUS_REGISTRY_URL = "quarkus.registry.url";
    static final String UPDATE_POM_ON_INSTALL = "updatePomOnInstall";

    public void afterProjectsRead(MavenSession session)
            throws MavenExecutionException {
        final String registryUrl = System.getProperty(QUARKUS_REGISTRY_URL);

        final DelegatingExecutionListener listener = new DelegatingExecutionListener();
        listener.add(session.getRequest().getExecutionListener());
        listener.add(new AbstractExecutionListener() {

            private MavenProject configProject;

            @Override
            public void mojoSucceeded(ExecutionEvent event) {
                final MavenProject project = event.getProject();
                final MojoExecution mojo = event.getMojoExecution();

                if (mojo.getGoal().equals("generate-platform-project")
                        && mojo.getArtifactId().equals("quarkus-platform-bom-maven-plugin")
                        && mojo.getGroupId().equals("io.quarkus")) {
                    configProject = project;
                }

                if (mojo.getArtifactId().equals("maven-install-plugin") && mojo.getGoal().equals("install")) {
                    if (project.getArtifactId().endsWith(Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX)) {
                        if (registryUrl != null) {
                            platformDescriptorReleased(registryUrl, event);
                        }
                        return;
                    }

                    if (project == configProject) {
                        final String updatePomOnInstall = (String) configProject.getProperties().get(UPDATE_POM_ON_INSTALL);
                        if (updatePomOnInstall != null && Boolean.parseBoolean(updatePomOnInstall)) {
                            final Path originalPom = project.getBasedir().toPath().resolve("pom.xml");
                            try {
                                IoUtils.copy(project.getFile().toPath(), originalPom);
                            } catch (IOException e) {
                                throw new RuntimeException(
                                        "Failed to replace the " + originalPom + " with " + project.getFile());
                            }
                        }
                    }
                }
            }

            private void platformDescriptorReleased(final String registryUrl, ExecutionEvent event) {
                final MojoExecution mojo = event.getMojoExecution();
                if (!(mojo.getArtifactId().equals("maven-install-plugin") && mojo.getGoal().equals("install"))) {
                    return;
                }

                final MavenProject project = event.getProject();
                final StringBuilder buf = new StringBuilder(registryUrl);
                buf.append("/").append(project.getGroupId());
                buf.append("/").append(project.getArtifactId());
                buf.append("/").append(project.getVersion());
                try {
                    final URL u = new URL(buf.toString());
                    URLConnection con = u.openConnection();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                        String line = reader.readLine();
                        while (line != null) {
                            System.out.println("REGISTRY: " + line);
                            line = reader.readLine();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        session.getRequest().setExecutionListener(listener);
    }
}
