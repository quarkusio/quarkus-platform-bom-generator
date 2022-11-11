package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.maven.dependency.ArtifactKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ProjectReleaseCollector {

    private Map<ReleaseOrigin, ReleaseOriginBuilder> originBuilders = new HashMap<>();

    ProjectRelease.Builder getOrCreateReleaseBuilder(ReleaseId releaseId, PlatformMember member) {
        final ReleaseOriginBuilder releaseBuilder = originBuilders
                .computeIfAbsent(releaseId.origin(), id -> new ReleaseOriginBuilder());
        releaseBuilder.members.putIfAbsent(member.key(), member);
        return releaseBuilder.builders.computeIfAbsent(releaseId.version(), id -> ProjectRelease.builder(releaseId));
    }

    Collection<Collection<ProjectRelease>> getOriginReleaseBuilders() {
        final Collection<Collection<ProjectRelease>> result = new ArrayList<>(originBuilders.size());
        for (ReleaseOriginBuilder originReleases : originBuilders.values()) {
            if (originReleases.isAlignConstraints()) {
                final Collection<ProjectRelease> releases = new ArrayList<>(originReleases.builders.size());
                for (ProjectRelease.Builder builder : originReleases.builders.values()) {
                    releases.add(builder.build());
                }
                result.add(releases);
            } else {
                for (ProjectRelease.Builder builder : originReleases.builders.values()) {
                    result.add(List.of(builder.build()));
                }
            }
        }
        return result;
    }

    void enforce(ProjectDependency preferred, ProjectDependency current) {
        final ReleaseOriginBuilder rob = originBuilders.get(current.releaseId().origin());
        if (rob == null) {
            return;
        }
        final ProjectRelease.Builder rb = rob.builders.get(current.releaseId().version());
        if (rb == null) {
            return;
        }
        System.out.println("ENFORCED " + preferred.artifact() + " for " + rob.members.keySet());
        final ProjectDependency preferredDep = ProjectDependency.create(current.releaseId(),
                current.dependency().setArtifact(current.artifact()));
        rb.add(preferredDep, (d1, d2) -> preferredDep);
    }

    private static class ReleaseOriginBuilder {
        final Map<ArtifactKey, PlatformMember> members = new HashMap<>();
        final Map<ReleaseVersion, ProjectRelease.Builder> builders = new HashMap<>();

        boolean isAlignConstraints() {
            return members.size() > 1 || members.values().iterator().next().config().isAlignOwnConstraints();
        }
    }
}
