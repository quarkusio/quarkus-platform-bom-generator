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

public class ProjectRelease {

    public class Builder {

        private Builder() {
        }

        private LinkedHashMap<ArtifactKey, ProjectDependency> deps = new LinkedHashMap<>();

        public ReleaseId id() {
            return id;
        }

        public Builder add(ProjectDependency dep) {
            final ProjectDependency existing = deps.put(dep.key(), dep);
            if (existing == null) {
                artifactVersions.add(dep.artifact().getVersion());
            } else if (!dep.artifact().getVersion().equals(existing.artifact().getVersion())) {
                throw new IllegalArgumentException(
                        "Failed to add " + dep + " since the release already includes " + existing);
            }
            return this;
        }

        public Builder add(Artifact a) {
            return add(ProjectDependency.create(id, a));
        }

        public Builder add(Dependency d) {
            return add(ProjectDependency.create(id, d));
        }

        public boolean includes(ArtifactKey key) {
            return deps.containsKey(key);
        }

        public ProjectRelease build() {
            ProjectRelease.this.deps = Collections.unmodifiableList(new ArrayList<>(deps.values()));
            return ProjectRelease.this;
        }
    }

    public static Builder builder(ReleaseId id) {
        return new ProjectRelease(id).new Builder();
    }

    public static ProjectRelease create(ReleaseId id, List<ProjectDependency> deps) {
        return new ProjectRelease(id, deps == null ? Collections.emptyList() : Collections.unmodifiableList(deps));
    }

    protected final ReleaseId id;
    protected List<ProjectDependency> deps;
    protected final Set<String> artifactVersions = new HashSet<String>();

    private ProjectRelease(ReleaseId id) {
        this(id, null);
    }

    private ProjectRelease(ReleaseId id, List<ProjectDependency> deps) {
        this.id = id;
        if (deps == null) {
            this.deps = new ArrayList<>();
        } else {
            this.deps = deps;
            for (ProjectDependency dep : deps) {
                artifactVersions.add(dep.artifact().getVersion());
            }
        }
    }

    public ReleaseId id() {
        return id;
    }

    public List<ProjectDependency> dependencies() {
        return deps;
    }

    public Collection<String> artifactVersions() {
        return artifactVersions;
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
        ProjectRelease other = (ProjectRelease) obj;
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
