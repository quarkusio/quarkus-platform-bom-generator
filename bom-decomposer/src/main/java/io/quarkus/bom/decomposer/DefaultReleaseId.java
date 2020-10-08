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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((origin == null) ? 0 : origin.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
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
        if (origin == null) {
            if (other.origin != null)
                return false;
        } else if (!origin.equals(other.origin))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
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