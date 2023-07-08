package io.quarkus.maven.project;

import java.util.List;
import java.util.Objects;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class MavenPluginBuilder {

    private final Plugin plugin;
    private MavenPluginConfigBuilder config;

    MavenPluginBuilder(String groupId, String artifactId, String version) {
        plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(Objects.requireNonNull(artifactId, "artifactId is null"));
        plugin.setVersion(version);
    }

    public MavenPluginBuilder setExtensions(boolean extensions) {
        plugin.setExtensions(true);
        return this;
    }

    public MavenPluginBuilder setInherited(boolean inherited) {
        plugin.setInherited(inherited);
        return this;
    }

    public MavenPluginExecutionBuilder addExecution(String... goals) {
        var e = new PluginExecution();
        e.setGoals(List.of(goals));
        plugin.addExecution(e);
        return new MavenPluginExecutionBuilder(e);
    }

    public MavenPluginConfigBuilder configure() {
        if (config == null) {
            var dom = new Xpp3Dom("configuration");
            plugin.setConfiguration(dom);
            config = new MavenPluginConfigBuilder(dom);
        }
        return config;
    }

    Plugin build() {
        return plugin;
    }
}
