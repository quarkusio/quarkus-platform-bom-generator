package io.quarkus.bom.decomposer;

import java.util.Objects;

public class DefaultReleaseId implements ReleaseId {

    final ReleaseOrigin origin;
    final ReleaseVersion version;

    public DefaultReleaseId(ReleaseOrigin origin, ReleaseVersion version) {
        this.origin = Objects.requireNonNull(origin);
        this.version = Objects.requireNonNull(version);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(origin);
        buf.append('#').append(version.asString());
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultReleaseId other = (DefaultReleaseId) obj;
        return Objects.equals(origin, other.origin) && Objects.equals(version, other.version);
    }

    @Override
    public ReleaseOrigin origin() {
        return origin;
    }

    @Override
    public ReleaseVersion version() {
        return version;
    }
}