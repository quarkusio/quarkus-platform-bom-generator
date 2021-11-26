package io.quarkus.bom.platform;

import io.quarkus.devtools.messagewriter.MessageWriter;
import org.eclipse.aether.artifact.Artifact;

public class ExtensionCoordsFilterFactory {

    public static ExtensionCoordsFilterFactory newInstance(PlatformBomConfig config, MessageWriter log) {
        return new ExtensionCoordsFilterFactory(config.isEnableNonMemberQuarkiverseExtensions());
    }

    private final boolean enableNonMemberQuarkiverseExtensions;

    private ExtensionCoordsFilterFactory(boolean enableNonMemberQuarkiverseExtensions) {
        this.enableNonMemberQuarkiverseExtensions = enableNonMemberQuarkiverseExtensions;
    }

    public ExtensionCoordsFilter forMember(final PlatformMember member) {
        return new ExtensionCoordsFilter() {
            @Override
            public boolean isExcludeFromBom(Artifact a) {
                if (member.originalBomCoords().getGroupId().equals(a.getGroupId())) {
                    return false;
                }
                if (!enableNonMemberQuarkiverseExtensions && a.getGroupId().startsWith("io.quarkiverse")) {
                    return true;
                }
                return false;
            }
        };
    }
}
