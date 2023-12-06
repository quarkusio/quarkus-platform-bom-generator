package io.quarkus.domino.tree.quarkus;

import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import java.util.Objects;

public class QuarkusPlatformInfo {

    private Member core;
    private List<Member> members;
    private ArtifactCoords mavenPlugin;

    public QuarkusPlatformInfo(Member core, List<Member> members, ArtifactCoords mavenPlugin) {
        this.core = core;
        this.members = List.copyOf(members);
        this.mavenPlugin = Objects.requireNonNull(mavenPlugin);
    }

    public Member getCore() {
        return core;
    }

    public List<Member> getMembers() {
        return members;
    }

    public ArtifactCoords getMavenPlugin() {
        return mavenPlugin;
    }

    public static class Member {
        private final ArtifactCoords bom;
        private final String quarkusCoreVersion;
        private final List<ArtifactCoords> extensions;
        private final Release release;

        public Member(ArtifactCoords bom, String quarkusCoreVersion, List<ArtifactCoords> extensions, Release release) {
            this.bom = bom;
            this.quarkusCoreVersion = quarkusCoreVersion;
            this.extensions = List.copyOf(extensions);
            this.release = release;
        }

        public ArtifactCoords getBom() {
            return bom;
        }

        public String getQuarkusCoreVersion() {
            return quarkusCoreVersion;
        }

        public List<ArtifactCoords> getExtensions() {
            return extensions;
        }

        public Release getRelease() {
            return release;
        }
    }

    public static class Release {
        private final String platformKey;
        private final String stream;
        private final String version;
        private final List<ArtifactCoords> memberBoms;

        public Release(String platformKey, String stream, String version, List<ArtifactCoords> memberBoms) {
            this.platformKey = Objects.requireNonNull(platformKey);
            this.stream = Objects.requireNonNull(stream);
            this.version = Objects.requireNonNull(version);
            this.memberBoms = List.copyOf(memberBoms);
        }

        public String getPlatformKey() {
            return platformKey;
        }

        public String getStream() {
            return stream;
        }

        public String getVersion() {
            return version;
        }

        public List<ArtifactCoords> getMemberBoms() {
            return memberBoms;
        }
    }
}
