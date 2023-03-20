package io.quarkus.domino.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ProductInfoImpl implements ProductInfo {

    private final String id;
    private final String group;
    private final String name;
    private final String type;
    private final String version;
    private final String stream;

    private ProductInfoImpl(ProductInfo other) {
        id = other.getId();
        group = other.getGroup();
        name = other.getName();
        type = other.getType();
        version = other.getVersion();
        stream = other.getStream();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getStream() {
        return stream;
    }

    /**
     * Public for Mojos
     */
    public static class Builder implements ProductInfo.Mutable {

        private String id;
        private String group;
        private String name;
        private String type;
        private String version;
        private String stream;

        /**
         * Public for Mojos
         */
        public Builder() {
        }

        Builder(ProductInfo other) {
            id = other.getId();
            group = other.getGroup();
            name = other.getName();
            type = other.getType();
            version = other.getVersion();
            stream = other.getStream();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getStream() {
            return stream;
        }

        @Override
        public ProductInfo build() {
            return new ProductInfoImpl(this);
        }

        @Override
        public Mutable setId(String id) {
            this.id = id;
            return this;
        }

        @Override
        public Mutable setGroup(String group) {
            this.group = group;
            return this;
        }

        @Override
        public Mutable setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Mutable setType(String type) {
            this.type = type;
            return this;
        }

        @Override
        public Mutable setVersion(String version) {
            this.version = version;
            return this;
        }

        @Override
        public Mutable setStream(String stream) {
            this.stream = stream;
            return this;
        }
    }
}
