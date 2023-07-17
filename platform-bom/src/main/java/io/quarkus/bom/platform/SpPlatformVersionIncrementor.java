package io.quarkus.bom.platform;

import io.quarkus.domino.RhVersionPattern;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class SpPlatformVersionIncrementor implements PlatformVersionIncrementor {

    @Override
    public String nextVersion(String baseVersion, String lastVersion) {
        var upstreamBaseVersion = RhVersionPattern.ensureNoRhQualifier(baseVersion);
        var rhQualifier = baseVersion.substring(upstreamBaseVersion.length());
        var v = new DefaultArtifactVersion(upstreamBaseVersion);

        if (lastVersion != null) {
            var prevBaseVersion = RhVersionPattern.ensureNoRhQualifier(lastVersion);
            var prevV = new DefaultArtifactVersion(prevBaseVersion);
            if (prevV.getMajorVersion() == v.getMajorVersion()
                    && prevV.getMinorVersion() == v.getMinorVersion()
                    && prevV.getIncrementalVersion() == v.getIncrementalVersion()
                    // compare qualifiers
                    && prevV.compareTo(v) > 0) {

                upstreamBaseVersion = prevBaseVersion;
                v = prevV;
            }
        }

        if (v.getQualifier() == null) {
            upstreamBaseVersion += ".SP1";
        } else if ("Final".equals(v.getQualifier())) {
            upstreamBaseVersion = upstreamBaseVersion.replace("Final", "SP1");
        } else if (v.getQualifier().startsWith("SP")) {
            int i = upstreamBaseVersion.lastIndexOf("SP");
            String suffix = upstreamBaseVersion.substring(i);
            String number = suffix.substring(2);
            String newSuffix = "SP" + (Integer.parseInt(number) + 1);
            upstreamBaseVersion = upstreamBaseVersion.replace(suffix, newSuffix);
        } else {
            upstreamBaseVersion += ".SP1";
        }
        return rhQualifier.isEmpty() ? upstreamBaseVersion : upstreamBaseVersion + rhQualifier;
    }
}
