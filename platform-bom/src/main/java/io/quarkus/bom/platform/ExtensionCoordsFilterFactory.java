package io.quarkus.bom.platform;

import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.ArtifactKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class ExtensionCoordsFilterFactory {

    public static ExtensionCoordsFilterFactory newInstance(PlatformBomConfig config, MessageWriter log) {
        final Map<ArtifactKey, PlatformBomMemberConfig> members = new HashMap<>();
        add(members, config.quarkusBom(), log);
        for (PlatformBomMemberConfig member : config.directDeps()) {
            add(members, member, log);
        }
        return new ExtensionCoordsFilterFactory(members, config.isEnableNonMemberQuarkiverseExtensions());
    }

    private static void add(final Map<ArtifactKey, PlatformBomMemberConfig> members, PlatformBomMemberConfig member,
            MessageWriter log) {
        final ArtifactKey memberKey = memberKey(member);
        if (memberKey != null) {
            final PlatformBomMemberConfig previous = members.put(memberKey, member);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Found members with the same GAV: " + previous.originalBomArtifact() + " and "
                                + member.originalBomArtifact());
            }
        } else {
            log.warn("Failed to determine the primary Maven artifact gropuId for member " + member.key());
        }
    }

    private static ArtifactKey memberKey(PlatformBomMemberConfig member) {
        if (member.isBom()) {
            return new ArtifactKey(member.originalBomArtifact().getGroupId(), member.originalBomArtifact().getArtifactId());
        }
        // we need to identify the extension groupIds for dependency filtering
        final Set<String> memberGroupIds = new HashSet<>(1);
        for (Dependency d : member.asDependencyConstraints()) {
            if (d.getArtifact().getArtifactId().endsWith("-deployment")) {
                memberGroupIds.add(d.getArtifact().getGroupId());
            }
        }
        if (memberGroupIds.size() == 1) {
            return new ArtifactKey(memberGroupIds.iterator().next(), member.generatedBomArtifact().getArtifactId());
        }
        return null;
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
