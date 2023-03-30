package io.quarkus.domino.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ProductInfoImpl implements ProductInfo {

    private final String id;
    private final String group;
    private final String name;
    private final String type;
    private final String purl;
    private final String version;
    private final String stream;
    private final String cpe;
    private final String description;
    private final ProductReleaseNotes releaseNotes;

    private ProductInfoImpl(ProductInfo other) {
        id = other.getId();
        group = other.getGroup();
        name = other.getName();
        type = other.getType();
        version = other.getVersion();
        purl = other.getPurl();
        stream = other.getStream();
        cpe = other.getCpe();
        description = other.getDescription();
        releaseNotes = other.getReleaseNotes();
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
    public String getPurl() {
        return purl;
    }

    @Override
    public String getStream() {
        return stream;
    }

    @Override
    public String getCpe() {
        return cpe;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ProductReleaseNotes getReleaseNotes() {
        return releaseNotes;
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
        private String purl;
        private String stream;
        private String cpe;
        private String description;
        private ProductReleaseNotes releaseNotes;

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
            purl = other.getPurl();
            stream = other.getStream();
            cpe = other.getCpe();
            description = other.getDescription();
            releaseNotes = other.getReleaseNotes();
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
        public String getPurl() {
            return purl;
        }

        @Override
        public String getStream() {
            return stream;
        }

        @Override
        public String getCpe() {
            return cpe;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public ProductReleaseNotes getReleaseNotes() {
            return releaseNotes;
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
        public Mutable setPurl(String purl) {
            this.purl = purl;
            return this;
        }

        @Override
        public Mutable setStream(String stream) {
            this.stream = stream;
            return this;
        }

        @Override
        public Mutable setCpe(String cpe) {
            this.cpe = cpe;
            return this;
        }

        @Override
        public Mutable setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Mutable setReleaseNotes(ProductReleaseNotes releaseNotes) {
            this.releaseNotes = releaseNotes;
            return this;
        }
    }
}
