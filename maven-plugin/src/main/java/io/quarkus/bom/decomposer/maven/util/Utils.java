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
import org.apache.maven.model.Model;

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

    public static void main(String[] args) throws Exception {

    }
}
