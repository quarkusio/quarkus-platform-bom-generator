package io.quarkus.maven.project;

import java.util.Objects;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class MavenPluginExecutionBuilder {

    private final PluginExecution e;
    private MavenPluginConfigBuilder config;

    MavenPluginExecutionBuilder(PluginExecution e) {
        this.e = Objects.requireNonNull(e, "execution is null");
    }

    public MavenPluginExecutionBuilder setId(String id) {
        e.setId(id);
        return this;
    }

    public MavenPluginExecutionBuilder setPhase(String phase) {
        e.setPhase(phase);
        return this;
    }

    public MavenPluginConfigBuilder configure() {
        if (config == null) {
            var dom = new Xpp3Dom("configuration");
            e.setConfiguration(dom);
            config = new MavenPluginConfigBuilder(dom);
        }
        return config;
    }
}
