package io.quarkus.bom.platform;

import io.quarkus.maven.ArtifactKey;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;

public class ExtensionCoordsFilterFactory {

    public static ExtensionCoordsFilterFactory newInstance(PlatformBomConfig config) {
        final Map<ArtifactKey, PlatformBomMemberConfig> members = new HashMap<>();
        add(members, config.quarkusBom());
        for (PlatformBomMemberConfig member : config.directDeps()) {
            if (member.isBom()) {
                add(members, member);
            }
        }
        return new ExtensionCoordsFilterFactory(members, config.isEnableNonMemberQuarkiverseExtensions());
    }

    private static void add(Map<ArtifactKey, PlatformBomMemberConfig> members, PlatformBomMemberConfig member) {
        final PlatformBomMemberConfig previous = members.put(
                new ArtifactKey(member.originalBomArtifact().getGroupId(), member.originalBomArtifact().getArtifactId()),
                member);
        if (previous != null) {
            throw new IllegalArgumentException("Found members with the same GAV: " + previous.originalBomArtifact() + " and "
                    + member.originalBomArtifact());
        }
    }

    private final Map<ArtifactKey, PlatformBomMemberConfig> members;
    private final boolean enableNonMemberQuarkiverseExtensions;

    private ExtensionCoordsFilterFactory(Map<ArtifactKey, PlatformBomMemberConfig> members,
            boolean enableNonMemberQuarkiverseExtensions) {
        this.members = members;
        this.enableNonMemberQuarkiverseExtensions = enableNonMemberQuarkiverseExtensions;
    }

    public ExtensionCoordsFilter forMember(final PlatformBomMemberConfig member) {
        return new ExtensionCoordsFilter() {
            @Override
            public boolean isExcludeFromBom(Artifact a) {
                if (member.originalBomArtifact().getGroupId().equals(a.getGroupId())) {
                    return false;
                }
                if (!enableNonMemberQuarkiverseExtensions && a.getGroupId().startsWith("io.quarkiverse")) {
                    return true;
                }
                for (ArtifactKey other : members.keySet()) {
                    if (other.getGroupId().equals(a.getGroupId())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
