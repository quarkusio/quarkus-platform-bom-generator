package io.quarkus.domino.cli.gradle;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class GradleModuleMetadata {

    private static volatile ObjectMapper mapper;

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            om.enable(JsonParser.Feature.ALLOW_COMMENTS);
            om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            om.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper = om;
        }
        return mapper;
    }

    public static GradleModuleMetadata deserialize(Path p) {
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            return getMapper().readValue(reader, GradleModuleMetadata.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize " + p, e);
        }
    }

    private Component component;
    private List<Variant> variants = Collections.emptyList();

    private Map<String, Object> any;

    @JsonIgnore
    public String getGroupId() {
        return component == null ? null : component.getGroup();
    }

    @JsonIgnore
    public String getArtifactId() {
        return component == null ? null : component.getModule();
    }

    @JsonIgnore
    public String getVersion() {
        return component == null ? null : component.getVersion();
    }

    @JsonIgnore
    public boolean isBom() {
        if (variants == null || variants.isEmpty()) {
            return false;
        }
        for (Variant v : variants) {
            if ("apiElements".equals(v.getName())) {
                return "platform".equals(v.getAttributes().get("org.gradle.category"));
            }
        }
        return false;
    }

    public Component getComponent() {
        return component;
    }

    public void setComponent(Component component) {
        this.component = component;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants;
    }

    public Map<String, Object> getAny() {
        return any;
    }

    @JsonAnySetter
    public void setAny(Map<String, Object> any) {
        this.any = any;
    }

    @Override
    public int hashCode() {
        return Objects.hash(any, component, variants);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GradleModuleMetadata other = (GradleModuleMetadata) obj;
        return Objects.equals(any, other.any) && Objects.equals(component, other.component)
                && Objects.equals(variants, other.variants);
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class Component {

        private String group;
        private String module;
        private String version;
        private Map<String, Object> any;

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getModule() {
            return module;
        }

        public void setModule(String module) {
            this.module = module;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Map<String, Object> getAny() {
            return any;
        }

        @JsonAnySetter
        public void setAny(Map<String, Object> any) {
            this.any = any;
        }

        @Override
        public int hashCode() {
            return Objects.hash(any, module, group, version);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Component other = (Component) obj;
            return Objects.equals(any, other.any) && Objects.equals(module, other.module)
                    && Objects.equals(group, other.group) && Objects.equals(version, other.version);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class Variant {

        private String name;
        private Map<String, String> attributes = Collections.emptyMap();
        private Map<String, Object> any = Collections.emptyMap();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }

        public Map<String, Object> getAny() {
            return any;
        }

        @JsonAnySetter
        public void setAny(Map<String, Object> any) {
            this.any = any;
        }

        @Override
        public int hashCode() {
            return Objects.hash(any, attributes, name);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Variant other = (Variant) obj;
            return Objects.equals(any, other.any) && Objects.equals(attributes, other.attributes)
                    && Objects.equals(name, other.name);
        }
    }
}
