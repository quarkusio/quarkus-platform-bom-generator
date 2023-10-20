package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.ProjectDependency;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class VersionConstraintComparator implements Comparator<String> {

    private final Collection<Pattern> versionPreferences;

    public VersionConstraintComparator(Collection<Pattern> versionPreferences) {
        this.versionPreferences = versionPreferences == null ? List.of() : versionPreferences;
    }

    @Override
    public int compare(String v1, String v2) {
        if (versionPreferences.isEmpty()) {
            return new DefaultArtifactVersion(v1).compareTo(new DefaultArtifactVersion(v2));
        }
        for (Pattern preference : versionPreferences) {
            if (preference.matcher(v1).matches()) {
                if (preference.matcher(v2).matches()) {
                    return new DefaultArtifactVersion(v1).compareTo(new DefaultArtifactVersion(v2));
                }
                return 1;
            }
            if (preference.matcher(v2).matches()) {
                return -1;
            }
        }
        return new DefaultArtifactVersion(v1).compareTo(new DefaultArtifactVersion(v2));
    }

    public boolean hasVersionPreferences() {
        return !versionPreferences.isEmpty();
    }

    public boolean isPreferredVersion(ProjectDependency d) {
        return isPreferredVersion(d.artifact().getVersion());
    }

    public boolean isPreferredVersion(String version) {
        return isPreferredVersion(new DefaultArtifactVersion(version));
    }

    public boolean isPreferredVersion(ArtifactVersion v) {
        if (versionPreferences.isEmpty()) {
            return false;
        }
        for (Pattern preference : versionPreferences) {
            if (preference.matcher(v.toString()).matches()) {
                return true;
            }
        }
        return false;
    }
}
