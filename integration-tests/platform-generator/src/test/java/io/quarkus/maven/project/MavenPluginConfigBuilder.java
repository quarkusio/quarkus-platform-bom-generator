package io.quarkus.maven.project;

import java.util.Objects;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class MavenPluginConfigBuilder {

    private final Xpp3Dom dom;

    MavenPluginConfigBuilder(Xpp3Dom dom) {
        this.dom = Objects.requireNonNull(dom, "dom is null");
    }

    public MavenPluginConfigBuilder setParameter(String name, String value) {
        var param = new Xpp3Dom(name);
        if (value != null) {
            param.setValue(value);
        }
        dom.addChild(param);
        return this;
    }

    public MavenPluginConfigBuilder configure(String name) {
        Objects.requireNonNull(name, "name is null");
        var dom = this.dom.getChild(name);
        if (dom == null) {
            dom = new Xpp3Dom(name);
            this.dom.addChild(dom);
        }
        return new MavenPluginConfigBuilder(dom);
    }

    public MavenPluginConfigBuilder configureNew(String name) {
        Objects.requireNonNull(name, "name is null");
        var dom = new Xpp3Dom(name);
        this.dom.addChild(dom);
        return new MavenPluginConfigBuilder(dom);
    }
}
