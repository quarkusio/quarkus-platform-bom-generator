package io.quarkus.domino.scm;

import io.quarkus.bom.decomposer.ReleaseOrigin;
import java.util.Objects;

public class ScmRepository implements ReleaseOrigin {

    public static ScmRepository ofUrl(String url) {
        return new ScmRepository(url, url);
    }

    public static ScmRepository ofId(String id) {
        return new ScmRepository(id, null);
    }

    private final String id;
    private final String url;

    private ScmRepository(String id, String url) {
        this.id = Objects.requireNonNull(id, "ID is null");
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public boolean hasUrl() {
        return url != null && !url.isEmpty();
    }

    @Override
    public boolean isUrl() {
        return hasUrl();
    }

    public String getUrl() {
        if (!hasUrl()) {
            throw new RuntimeException(id + " was not initialized with a URL");
        }
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ScmRepository that = (ScmRepository) o;
        return Objects.equals(id, that.id) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url);
    }

    @Override
    public String toString() {
        return id;
    }
}
