package io.quarkus.bom.platform.version;

import io.quarkus.domino.RhVersionPattern;
import io.quarkus.domino.pnc.PncVersionProvider;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class PncVersionIncrementor implements PlatformVersionIncrementor {

    private final PlatformVersionIncrementor baseIncrementor;

    public PncVersionIncrementor() {
        this(null);
    }

    public PncVersionIncrementor(PlatformVersionIncrementor baseIncrementor) {
        this.baseIncrementor = baseIncrementor;
    }

    @Override
    public String nextVersion(String groupId, String artifactId, String baseVersion, String lastVersion) {
        if (baseIncrementor != null) {
            var baseNextVersion = baseIncrementor.nextVersion(groupId, artifactId, baseVersion, lastVersion);
            return PncVersionProvider.getNextRedHatBuildVersion(groupId, artifactId, baseNextVersion);
        }

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

        return PncVersionProvider.getNextRedHatBuildVersion(groupId, artifactId, v.toString());
    }
}
