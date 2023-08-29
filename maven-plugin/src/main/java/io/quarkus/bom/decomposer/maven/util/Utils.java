package io.quarkus.bom.decomposer.maven.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;

public class Utils {

    public static Model newModel() {
        final Model model = new Model() {
            @Override
            public void setProperties(Properties props) {
                if (!(props instanceof SortedProperties)) {
                    final Properties sorted = new SortedProperties();
                    for (Map.Entry<?, ?> prop : props.entrySet()) {
                        sorted.setProperty(prop.getKey().toString(),
                                prop.getValue() == null ? null : prop.getValue().toString());
                    }
                    props = sorted;
                }
                super.setProperties(props);
            }
        };
        model.setProperties(new SortedProperties());
        model.setModelVersion("4.0.0");
        return model;
    }

    public static void skipInstallAndDeploy(final Model pom) {
        disablePlugin(pom, "maven-install-plugin", "default-install");
        disablePlugin(pom, "maven-deploy-plugin", "default-deploy");
        pom.getProperties().setProperty("gpg.skip", "true");
        pom.getProperties().setProperty("skipNexusStagingDeployMojo", "true");
    }

    public static void disablePlugin(Model pom, String pluginArtifactId, String execId) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        PluginManagement pm = build.getPluginManagement();
        if (pm == null) {
            pm = new PluginManagement();
            build.setPluginManagement(pm);
        }
        Plugin plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId(pluginArtifactId);
        PluginExecution e = new PluginExecution();
        plugin.addExecution(e);
        e.setId(execId);
        e.setPhase("none");
    }

    private static class SortedProperties extends Properties {
        @Override
        public Enumeration<Object> keys() {
            final List<String> sorted = sortedKeys();
            final Vector<Object> keyList = new Vector<Object>(sorted.size());
            for (String s : sorted) {
                keyList.add(s);
            }
            return keyList.elements();
        }

        @Override
        public Set<Object> keySet() {
            final List<String> sorted = sortedKeys();
            final LinkedHashSet<Object> result = new LinkedHashSet<>(sorted.size());
            result.addAll(sorted);
            return result;
        }

        private List<String> sortedKeys() {
            final Set<Object> originalKeys = super.keySet();
            final List<String> sorted = new ArrayList<>(size());
            for (Object o : originalKeys) {
                sorted.add(o == null ? null : o.toString());
            }
            Collections.sort(sorted);
            return sorted;
        }

    }
}
