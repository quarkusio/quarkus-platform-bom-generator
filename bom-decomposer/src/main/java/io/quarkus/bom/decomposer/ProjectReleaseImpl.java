package io.quarkus.bom.decomposer;

import io.quarkus.maven.ArtifactKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

class ProjectReleaseImpl implements ProjectRelease {

    public class Builder implements ProjectRelease.Builder {

        private Builder() {
        }

        private LinkedHashMap<ArtifactKey, ProjectDependency> deps = new LinkedHashMap<>();

        @Override
        public ReleaseId id() {
            return id;
        }

        @Override
        public Builder add(ProjectDependency dep) {
            final ProjectDependency existing = deps.put(dep.key(), dep);
            if (existing == null) {
                artifactVersions.add(dep.artifact().getVersion());
            } else if (!dep.artifact().getVersion().equals(existing.artifact().getVersion())) {
                throw new IllegalArgumentException(
                        "Failed to add " + dep + " since the release already includes " + existing);
            }
            artifactVersions.add(dep.artifact().getVersion());
            groupIds.add(dep.artifact.getGroupId());
            return this;
        }

        @Override
        public Builder add(Artifact a) {
            return add(ProjectDependency.create(id, a));
        }

        @Override
        public Builder add(Dependency d) {
            return add(ProjectDependency.create(id, d));
        }

        @Override
        public boolean includes(ArtifactKey key) {
            return deps.containsKey(key);
        }

        @Override
        public Collection<ProjectDependency> dependencies() {
            return deps.values();
        }

        @Override
        public Collection<String> artifactVersions() {
            return artifactVersions;
        }

        @Override
        public Collection<String> groupIds() {
            return groupIds;
        }

        @Override
        public ProjectRelease build() {
            ProjectReleaseImpl.this.deps = Collections.unmodifiableList(new ArrayList<>(deps.values()));
            return ProjectReleaseImpl.this;
        }
    }

    public static Builder builder(ReleaseId id) {
        return new ProjectReleaseImpl(id).new Builder();
    }

    public static ProjectRelease create(ReleaseId id, List<ProjectDependency> deps) {
        return new ProjectReleaseImpl(id, deps == null ? Collections.emptyList() : Collections.unmodifiableList(deps));
    }

    protected final ReleaseId id;
    protected List<ProjectDependency> deps;
    protected final Set<String> artifactVersions = new HashSet<String>();
    protected final Set<String> groupIds = new HashSet<>(1);

    private ProjectReleaseImpl(ReleaseId id) {
        this(id, null);
    }

    private ProjectReleaseImpl(ReleaseId id, List<ProjectDependency> deps) {
        this.id = id;
        this.deps = deps == null ? List.of() : deps;
    }

    @Override
    public ReleaseId id() {
        return id;
    }

    @Override
    public Collection<ProjectDependency> dependencies() {
        return deps;
    }

    @Override
    public Collection<String> artifactVersions() {
        return artifactVersions;
    }

    @Override
    public Collection<String> groupIds() {
        return groupIds;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((deps == null) ? 0 : deps.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProjectReleaseImpl other = (ProjectReleaseImpl) obj;
        if (deps == null) {
            if (other.deps != null)
                return false;
        } else if (!deps.equals(other.deps))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }
}
