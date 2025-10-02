package io.quarkus.domino.recipes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Comparator;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * A maven artifact identifier
 */
public final class GAV implements Comparable<GAV> {

    private static final String GAV_FORMAT = "%s:%s:%s";

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String tag;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public GAV(@JsonProperty("groupId") String groupId, @JsonProperty("artifactId") String artifactId,
            @JsonProperty("version") String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.tag = null;
    }

    private GAV(String groupId, String artifactId, String version, String tag) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.tag = tag;
    }

    public static GAV parse(String gav) {
        var parts = gav.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a GAV: " + gav);
        }
        return create(parts[0], parts[1], parts[2]);
    }

    public static GAV create(String groupId, String artifactId, String version) {
        String tag = DigestUtils.sha256Hex(String.format(GAV_FORMAT, groupId, artifactId, version));
        return new GAV(groupId, artifactId, version, tag);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.groupId);
        hash = 23 * hash + Objects.hashCode(this.artifactId);
        hash = 23 * hash + Objects.hashCode(this.version);
        hash = 23 * hash + Objects.hashCode(this.tag);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GAV other = (GAV) obj;
        if (!Objects.equals(this.groupId, other.groupId)) {
            return false;
        }
        if (!Objects.equals(this.artifactId, other.artifactId)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        return Objects.equals(this.tag, other.tag);
    }

    public String stringForm() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String toString() {
        return "GAV{" + "groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", tag=" + tag + '}';
    }

    @Override
    public int compareTo(GAV o) {
        return Comparator.comparing(GAV::getGroupId)
                .thenComparing(GAV::getArtifactId)
                .thenComparing(GAV::getVersion)
                .thenComparing(GAV::getTag, Comparator.nullsFirst(Comparator.naturalOrder()))
                .compare(this, o);
    }
}
