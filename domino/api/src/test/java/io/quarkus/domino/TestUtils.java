package io.quarkus.domino;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

public class TestUtils {

    public static Path getResource(String resourceName) {
        var url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        assertThat(url).isNotNull();
        return toLocalPath(url);
    }

    public static Path toLocalPath(final URL url) {
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException var2) {
            throw new IllegalArgumentException("Failed to translate " + url + " to local path", var2);
        }
    }
}
