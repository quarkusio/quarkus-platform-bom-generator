package io.quarkus.bom.platform;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ArtifactVersion;

public class VersionConstraintComparator implements Comparator<ArtifactVersion> {

    private final Collection<Pattern> versionPreferences;

    public VersionConstraintComparator(Collection<Pattern> versionPreferences) {
        this.versionPreferences = versionPreferences == null ? List.of() : versionPreferences;
    }

    @Override
    public int compare(ArtifactVersion o1, ArtifactVersion o2) {
        if (versionPreferences.isEmpty()) {
            return o1.compareTo(o2);
        }
        for (Pattern preference : versionPreferences) {
            if (preference.matcher(o1.toString()).matches()) {
                if (preference.matcher(o2.toString()).matches()) {
                    return o1.compareTo(o2);
                }
                return 1;
            }
            if (preference.matcher(o2.toString()).matches()) {
                return -1;
            }
        }
        return o1.compareTo(o2);
    }

    public boolean hasVersionPreferences() {
        return !versionPreferences.isEmpty();
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
