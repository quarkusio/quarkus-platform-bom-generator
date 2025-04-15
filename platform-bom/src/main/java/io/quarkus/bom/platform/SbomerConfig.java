package io.quarkus.bom.platform;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SbomerConfig {

    private String apiVersion;
    private String type;
    private List<SbomerProductConfig> products;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<SbomerProductConfig> getProducts() {
        return products;
    }

    public void setProducts(List<SbomerProductConfig> products) {
        this.products = products;
    }

    public void addProduct(SbomerProductConfig product) {
        Objects.requireNonNull(product);
        if (products == null) {
            products = new ArrayList<>();
        }
        products.add(product);
    }

    public void serialize(Path file) {
        final Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory " + parent, e);
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            initYamlMapper().writeValue(writer, this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize SBOMER config", e);
        }
    }

    public static SbomerConfig deserialize(Path yaml) {
        if (!Files.exists(yaml)) {
            throw new IllegalArgumentException("File " + yaml + " does not exist");
        }
        try (BufferedReader reader = Files.newBufferedReader(yaml)) {
            return initYamlMapper().readValue(reader, SbomerConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize " + SbomerConfig.class.getName() + " from " + yaml, e);
        }
    }

    private static ObjectMapper initYamlMapper() {
        return initMapper(new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.SPLIT_LINES)
                        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)));
    }

    private static ObjectMapper initMapper(ObjectMapper mapper) {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
