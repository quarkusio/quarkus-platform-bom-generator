package io.quarkus.bom.platform;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ArtifactVersion;

public class VersionConstraintComparator implements Comparator<ArtifactVersion> {

    private final Collection<Pattern> versionPreferences;

    public VersionConstraintComparator(Collection<Pattern> versionPreferences) {
        this.versionPreferences = versionPreferences == null ? Collections.emptyList() : versionPreferences;
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
}
