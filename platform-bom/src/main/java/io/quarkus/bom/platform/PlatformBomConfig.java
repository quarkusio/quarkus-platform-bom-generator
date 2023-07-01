package io.quarkus.bom.platform;

import io.quarkus.bom.PomResolver;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;

public class PlatformBomConfig {

    public static class Builder {

        final PlatformBomConfig config = new PlatformBomConfig();

        private Builder() {
        }

        public Builder platformBom(Artifact platformBom) {
            config.bomArtifact = platformBom;
            return this;
        }

        public Builder includePlatformProperties(boolean includePlatformProperties) {
            config.includePlatformProperties = includePlatformProperties;
            return this;
        }

        public Builder disableGroupAlignmentToPreferredVersions(boolean disableGroupAlignmentToPreferredVersions) {
            config.disableGroupAlignmentToPreferredVersions = disableGroupAlignmentToPreferredVersions;
            return this;
        }

        public Builder addMember(PlatformMember member) {
            if (member.getInputBom() != null
                    && member.getInputBom().getArtifactId().equals("quarkus-bom")
                    && member.getInputBom().getGroupId().equals("io.quarkus")) {
                config.quarkusBom = member;
            } else {
                config.directDeps.add(member);
            }
            return this;
        }

        public Builder pomResolver(PomResolver pomResolver) {
            config.bomResolver = pomResolver;
            return this;
        }

        public Builder enforce(Artifact artifact) {
            config.enforced.put(ArtifactKey.of(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                    artifact.getExtension()), artifact);
            return this;
        }

        public Builder excludeGroupId(String groupId) {
            config.excludedGroups.add(groupId);
            return this;
        }

        public Builder exclude(String groupId, String artifactId) {
            return exclude(ArtifactKey.of(groupId, artifactId, null, ArtifactCoords.TYPE_JAR));
        }

        public Builder exclude(ArtifactKey key) {
            if (key.getClassifier().equals("*")) {
                config.excluded.put(getNonClassifiedKey(key), true);
            } else {
                config.excluded.put(key, false);
            }
            return this;
        }

        public Builder enableNonMemberQuarkiverseExtensions(boolean enableNonMemberQuarkiverseExtensions) {
            config.enableNonMemberQuarkiverseExtensions = enableNonMemberQuarkiverseExtensions;
            return this;
        }

        public Builder artifactResolver(ArtifactResolver resolver) {
            config.artifactResolver = resolver;
            return this;
        }

        public Builder versionConstraintPreference(List<String> preferences) {
            config.versionConstraintPreferences = preferences;
            return this;
        }

        public Builder foreignPreferredConstraint(int flags) {
            config.foreignPreferredConstraint = flags;
            return this;
        }

        public PlatformBomConfig build() {
            Objects.requireNonNull(config.bomResolver);
            if (config.bomArtifact == null) {
                throw new IllegalArgumentException("The platform BOM artifact has not been provided");
            }
            if (config.quarkusBom == null) {
                throw new IllegalArgumentException("The Quarkus BOM artifact has not been provided");
            }
            return config;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private PomResolver bomResolver;
    private Artifact bomArtifact;
    private boolean includePlatformProperties;
    private PlatformMember quarkusBom;
    private List<PlatformMember> directDeps = new ArrayList<>();
    private Map<ArtifactKey, Artifact> enforced = new HashMap<>(0);
    private Map<ArtifactKey, Boolean> excluded = new HashMap<>();
    private Set<String> excludedGroups = new HashSet<>(0);
    private boolean enableNonMemberQuarkiverseExtensions;
    private ArtifactResolver artifactResolver;
    private List<String> versionConstraintPreferences = Collections.emptyList();
    private int foreignPreferredConstraint = (ForeignPreferredConstraint.WARN.flag()
            | ForeignPreferredConstraint.ACCEPT_IF_COMPATIBLE.flag());
    private boolean disableGroupAlignmentToPreferredVersions;

    private PlatformBomConfig() {
    }

    public PomResolver bomResolver() {
        return bomResolver;
    }

    public Artifact bomArtifact() {
        return bomArtifact;
    }

    public boolean includePlatformProperties() {
        return includePlatformProperties;
    }

    public PlatformMember quarkusBom() {
        return quarkusBom;
    }

    public List<PlatformMember> externalMembers() {
        return directDeps;
    }

    public boolean hasEnforced() {
        return !enforced.isEmpty();
    }

    public Map<ArtifactKey, Artifact> enforced() {
        return enforced;
    }

    public Artifact enforced(ArtifactKey key) {
        return enforced.get(key);
    }

    public boolean hasExcluded() {
        return !excluded.isEmpty();
    }

    public boolean isEnableNonMemberQuarkiverseExtensions() {
        return enableNonMemberQuarkiverseExtensions;
    }

    public boolean isDisableGroupAlignmentToPreferredVersions() {
        return disableGroupAlignmentToPreferredVersions;
    }

    public ArtifactResolver artifactResolver() {
        return artifactResolver;
    }

    public List<String> versionConstraintPreferences() {
        return versionConstraintPreferences;
    }

    public int foreignPreferredConstraint() {
        return foreignPreferredConstraint;
    }

    boolean excluded(ArtifactKey key) {
        Boolean wildcard = excluded.get(key);
        // if the key is found in the excluded set, it's excluded
        if (wildcard != null) {
            return true;
        }
        // if the classifier is empty, it's not excluded
        if (key.getClassifier().isEmpty() || Boolean.TRUE.equals(wildcard)) {
            return false;
        }
        // if the maven G:A is either not excluded or excluded but not with a wildcard, the key shouldn't be excluded
        wildcard = excluded.get(getNonClassifiedKey(key));
        if (wildcard == null || Boolean.FALSE.equals(wildcard)) {
            return false;
        }
        return true;
    }

    private static ArtifactKey getNonClassifiedKey(ArtifactKey key) {
        return ArtifactKey.of(key.getGroupId(), key.getArtifactId(), ArtifactCoords.DEFAULT_CLASSIFIER,
                ArtifactCoords.TYPE_JAR);
    }
}
