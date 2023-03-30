package io.quarkus.domino.manifest;

import io.quarkus.domino.ProjectDependencyConfigMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * This type would mostly map to a Cyclone DX component
 */
public interface ProductInfo {

    String getId();

    String getStream();

    String getGroup();

    String getName();

    String getType();

    String getVersion();

    String getPurl();

    String getCpe();

    String getDescription();

    ProductReleaseNotes getReleaseNotes();

    default Mutable mutable() {
        return new ProductInfoImpl.Builder(this);
    }

    /**
     * Persist this configuration to the specified file.
     *
     * @param p Target path
     * @throws IOException if the specified file can not be written to.
     */
    default void persist(Path p) throws IOException {
        ProjectDependencyConfigMapper.serialize(this, p);
    }

    interface Mutable extends ProductInfo {

        Mutable setId(String id);

        Mutable setGroup(String group);

        Mutable setName(String name);

        Mutable setType(String type);

        Mutable setVersion(String version);

        Mutable setPurl(String purl);

        Mutable setStream(String stream);

        Mutable setCpe(String cpe);

        Mutable setDescription(String description);

        Mutable setReleaseNotes(ProductReleaseNotes notes);

        ProductInfo build();

        default void persist(Path p) throws IOException {
            ProjectDependencyConfigMapper.serialize(build(), p);
        }
    }

    /**
     * @return a new mutable instance
     */
    static Mutable builder() {
        return new ProductInfoImpl.Builder();
    }

    /**
     * Read build info from the specified file
     *
     * @param path File to read from (yaml or json)
     * @return read-only {@link ProductInfo} object
     * @throws IOException in case of a failure
     */
    static ProductInfo fromFile(Path path) throws IOException {
        return mutableFromFile(path).build();
    }

    /**
     * Read config from the specified file
     *
     * @param path File to read from (yaml or json)
     * @return mutable {@link ProductInfo} instance
     * @throws IOException in case of a failure
     */
    static ProductInfo.Mutable mutableFromFile(Path path) throws IOException {
        final ProductInfo.Mutable mutable = ProjectDependencyConfigMapper.deserialize(path,
                ProductInfoImpl.Builder.class);
        return mutable == null ? ProductInfo.builder() : mutable;
    }

    /**
     * Read config from an input stream
     *
     * @param inputStream input stream to read from
     * @return read-only {@link ProductInfo} object (empty/default for an empty file)
     * @throws IOException in case of a failure
     */
    static ProductInfo fromStream(InputStream inputStream) throws IOException {
        final ProductInfo.Mutable mutable = ProjectDependencyConfigMapper.deserialize(inputStream,
                ProductInfoImpl.Builder.class);
        return mutable == null ? ProductInfo.builder().build() : mutable.build();
    }
}
