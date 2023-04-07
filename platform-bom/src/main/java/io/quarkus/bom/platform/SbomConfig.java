package io.quarkus.bom.platform;

import io.quarkus.domino.ProductInfo;
import io.quarkus.domino.ProductReleaseNotes;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SbomConfig {

    public static final String ALL = "ALL";
    public static final String INCLUSIONS = "INCLUSIONS";
    public static final String NONE = "NONE";
    private static final Set<String> DEPS_TO_BUILD_VALUES = Set.of(ALL, INCLUSIONS, NONE);

    private ProductConfig product;
    private boolean supportedExtensionsOnly;
    private String applyDependenciesToBuildConfig;

    public ProductConfig getProductInfo() {
        return product;
    }

    public void setProductInfo(ProductConfig product) {
        this.product = product;
    }

    public boolean isSupportedExtensionsOnly() {
        return supportedExtensionsOnly;
    }

    public void setSupportedExtensionsOnly(boolean supportedExtensionsOnly) {
        this.supportedExtensionsOnly = supportedExtensionsOnly;
    }

    public String getApplyDependenciesToBuildConfig() {
        return applyDependenciesToBuildConfig;
    }

    public void setApplyDependenciesToBuildConfig(String applyDependenciesToBuildConfig) {
        ensureValidDepsToBuildConfig(applyDependenciesToBuildConfig);
        this.applyDependenciesToBuildConfig = applyDependenciesToBuildConfig;
    }

    public boolean isApplyCompleteDependenciesToBuildConfig() {
        ensureValidDepsToBuildConfig(applyDependenciesToBuildConfig);
        return ALL.equalsIgnoreCase(applyDependenciesToBuildConfig);
    }

    public boolean isApplyDependenciesToBuildInclusions() {
        ensureValidDepsToBuildConfig(applyDependenciesToBuildConfig);
        return applyDependenciesToBuildConfig == null || INCLUSIONS.equalsIgnoreCase(applyDependenciesToBuildConfig);
    }

    private static void ensureValidDepsToBuildConfig(String applyDependenciesToBuildConfig) {
        if (applyDependenciesToBuildConfig != null && !DEPS_TO_BUILD_VALUES.contains(applyDependenciesToBuildConfig)) {
            throw new IllegalArgumentException("applyDependenciesToBuildConfig allows one of " + DEPS_TO_BUILD_VALUES
                    + " but got " + applyDependenciesToBuildConfig);
        }
    }

    /**
     * Product release information that will be added to the generated SBOM
     */
    public static class ProductConfig {

        public static ProductInfo toProductInfo(ProductConfig productConfig) {
            if (productConfig == null) {
                return null;
            }
            var pi = ProductInfo.builder()
                    .setId(productConfig.getId())
                    .setStream(productConfig.getStream())
                    .setGroup(productConfig.getGroup())
                    .setName(productConfig.getName())
                    .setType(productConfig.getType())
                    .setVersion(productConfig.getVersion())
                    .setPurl(productConfig.getPurl())
                    .setDescription(productConfig.getDescription())
                    .setCpe(productConfig.getCpe());
            if (productConfig.getReleaseNotes() != null) {
                var rnConfig = productConfig.getReleaseNotes();
                pi.setReleaseNotes(
                        ProductReleaseNotes.builder()
                                .setTitle(rnConfig.getTitle())
                                .setType(rnConfig.getType())
                                .setAliases(rnConfig.getAliases())
                                .setProperties(rnConfig.getProperties())
                                .build());
            }
            return pi;
        }

        private String id;
        private String stream;
        private String group;
        private String name;
        private String type = "FRAMEWORK";
        private String version;
        private String purl;
        private String cpe;
        private String description;
        private ReleaseNotes releaseNotes;

        /**
         * Product ID for PST to identify the product.
         *
         * @return product ID for PST to identify the product
         */
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        /**
         * Product release stream
         *
         * @return product release stream
         */
        public String getStream() {
            return stream;
        }

        public void setStream(String stream) {
            this.stream = stream;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /**
         * Product type to appear in the generated SBOM. The default value is FRAMEWORK.
         *
         * @return product type
         */
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        /**
         * Package URL of the top level product component. If not specified, will be derived from the rest of the confguration.
         *
         * @return package URL of the top level product component
         */
        public String getPurl() {
            return purl;
        }

        public void setPurl(String purl) {
            this.purl = purl;
        }

        public String getCpe() {
            return cpe;
        }

        public void setCpe(String cpe) {
            this.cpe = cpe;
        }

        /**
         * Product description
         *
         * @return product description
         */
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ReleaseNotes getReleaseNotes() {
            return releaseNotes;
        }

        public void setReleaseNotes(ReleaseNotes releaseNotes) {
            this.releaseNotes = releaseNotes;
        }
    }

    public static class ReleaseNotes {

        private String type;
        private String title;
        private List<String> aliases = List.of();
        private Map<String, String> properties = Map.of();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
