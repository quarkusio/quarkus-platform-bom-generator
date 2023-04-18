package io.quarkus.domino;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ProductReleaseNotesImpl implements ProductReleaseNotes {

    private final String type;
    private final String title;
    private final List<String> aliases;
    private final Map<String, String> properties;

    private ProductReleaseNotesImpl(ProductReleaseNotes other) {
        this.type = other.getType();
        this.title = other.getTitle();
        this.aliases = List.copyOf(other.getAliases());
        this.properties = toSortedUnmodifiableMap(other.getProperties());
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    static ProductReleaseNotes.Mutable builder() {
        return new ProductReleaseNotesImpl.Builder();
    }

    public static class Builder implements ProductReleaseNotes.Mutable {
        private String type;
        private String title;
        private List<String> aliases = List.of();
        private Map<String, String> properties = Map.of();

        public Builder() {
        }

        Builder(ProductReleaseNotes other) {
            this.type = other.getType();
            this.title = other.getTitle();
            this.aliases = List.copyOf(other.getAliases());
            this.properties = Map.copyOf(other.getProperties());
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Mutable setType(String type) {
            this.type = type;
            return this;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public Mutable setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public List<String> getAliases() {
            return aliases;
        }

        @Override
        public Mutable setAliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public Mutable setProperties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        @Override
        public ProductReleaseNotes build() {
            return new ProductReleaseNotesImpl(this);
        }
    }

    private static <K extends Comparable<K>, V> Map<K, V> toSortedUnmodifiableMap(Map<K, V> map) {
        if (map.isEmpty()) {
            return Map.of();
        }
        if (map.size() == 1) {
            return Map.copyOf(map);
        }
        return Collections.unmodifiableMap(new TreeMap<>(map));
    }
}
