package com.redhat.hacbs.recipes.location;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The results of path matching a specific GAV from a repository database
 *
 * The paths are listed from least specific to mast specific, so files in the most specific
 * path will be used preferentially.
 *
 *
 */
public class RecipePathMatch {

    final Path group;
    final Path artifact;
    final Path version;
    final Path artifactAndVersion;
    /**
     * If this is true then there are no artifact or version overrides for this group id, meaning that
     * every artifact with this group id will use the same recipes.
     */
    final boolean groupAuthoritative;

    public RecipePathMatch(Path group, Path artifact, Path version, Path artifactAndVersion, boolean groupIsAuthoritative) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.artifactAndVersion = artifactAndVersion;
        this.groupAuthoritative = groupIsAuthoritative;
    }

    public Path getGroup() {
        return group;
    }

    public Path getArtifact() {
        return artifact;
    }

    public Path getVersion() {
        return version;
    }

    public Path getArtifactAndVersion() {
        return artifactAndVersion;
    }

    public boolean isGroupAuthoritative() {
        return groupAuthoritative;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RecipePathMatch that = (RecipePathMatch) o;
        return Objects.equals(group, that.group) && Objects.equals(artifact, that.artifact)
                && Objects.equals(version, that.version) && Objects.equals(artifactAndVersion, that.artifactAndVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, artifact, version, artifactAndVersion);
    }
}
