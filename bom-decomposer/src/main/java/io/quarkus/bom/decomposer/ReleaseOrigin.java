package io.quarkus.bom.decomposer;

import java.util.Objects;

public interface ReleaseOrigin extends Comparable<ReleaseOrigin> {

    class Factory {
        public static ReleaseOrigin scmConnection(String connection) {
            return new ScmConnectionOrigin(connection);
        }

        public static ReleaseOrigin ga(String groupId, String artifactId) {
            return new GaOrigin(groupId, artifactId);
        }
    }

    class ScmConnectionOrigin implements ReleaseOrigin {

        final String connection;

        ScmConnectionOrigin(String connection) {
            this.connection = Objects.requireNonNull(connection);
        }

        @Override
        public String toString() {
            return connection;
        }

        @Override
        public int hashCode() {
            return Objects.hash(connection);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ScmConnectionOrigin other = (ScmConnectionOrigin) obj;
            return Objects.equals(connection, other.connection);
        }

        @Override
        public int compareTo(ReleaseOrigin o) {
            return toString().compareTo(o.toString());
        }
    }

    class GaOrigin implements ReleaseOrigin {
        final String groupId;
        final String artifactId;

        GaOrigin(String groupId, String artifactId) {
            this.groupId = Objects.requireNonNull(groupId);
            this.artifactId = Objects.requireNonNull(artifactId);
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactId, groupId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            GaOrigin other = (GaOrigin) obj;
            return Objects.equals(artifactId, other.artifactId) && Objects.equals(groupId, other.groupId);
        }

        @Override
        public int compareTo(ReleaseOrigin o) {
            return toString().compareTo(o.toString());
        }
    }
}
