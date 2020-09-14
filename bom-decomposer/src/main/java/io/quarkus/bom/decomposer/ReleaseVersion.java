package io.quarkus.bom.decomposer;

import java.util.Objects;

public interface ReleaseVersion {

	class Factory {
		public static ReleaseVersion tag(String tag) {
			return new StringReleaseVersion("Tag", tag);
		}
		public static ReleaseVersion version(String version) {
			return new StringReleaseVersion("Version", version);
		}
	}

	String asString();
	
	class StringReleaseVersion implements ReleaseVersion {
		final String type;
		final String value;
		StringReleaseVersion(String type, String value) {
			this.type = Objects.requireNonNull(type);
			this.value = Objects.requireNonNull(value);
		}
		@Override
		public String asString() {
			return value;
		}
		@Override
		public String toString() {
			return type + ": " + value;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((type == null) ? 0 : type.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			StringReleaseVersion other = (StringReleaseVersion) obj;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}
	}
}
