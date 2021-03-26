package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.registry.Constants;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
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

    public void afterProjectsRead(MavenSession session)
            throws MavenExecutionException {
        final DelegatingExecutionListener listener = new DelegatingExecutionListener();
        listener.add(session.getRequest().getExecutionListener());
        listener.add(new AbstractExecutionListener() {
            @Override
            public void mojoSucceeded(ExecutionEvent event) {
                final MavenProject project = event.getProject();
                if (!project.getArtifactId().endsWith(Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX)) {
                    return;
                }
                final MojoExecution mojo = event.getMojoExecution();
                if (!(mojo.getArtifactId().equals("maven-install-plugin") && mojo.getGoal().equals("install"))) {
                    return;
                }

                final StringBuilder buf = new StringBuilder("http://localhost:8080/registry/new-platform");
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
