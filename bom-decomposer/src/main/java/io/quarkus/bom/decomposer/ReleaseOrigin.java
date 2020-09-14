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
			final int prime = 31;
			int result = 1;
			result = prime * result + ((connection == null) ? 0 : connection.hashCode());
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
			ScmConnectionOrigin other = (ScmConnectionOrigin) obj;
			if (connection == null) {
				if (other.connection != null)
					return false;
			} else if (!connection.equals(other.connection))
				return false;
			return true;
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
			final int prime = 31;
			int result = 1;
			result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
			result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
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
			GaOrigin other = (GaOrigin) obj;
			if (artifactId == null) {
				if (other.artifactId != null)
					return false;
			} else if (!artifactId.equals(other.artifactId))
				return false;
			if (groupId == null) {
				if (other.groupId != null)
					return false;
			} else if (!groupId.equals(other.groupId))
				return false;
			return true;
		}
		@Override
		public int compareTo(ReleaseOrigin o) {
			return toString().compareTo(o.toString());
		}
	}
}
