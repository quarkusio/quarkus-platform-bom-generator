package io.quarkus.bom.decomposer;

import io.quarkus.maven.dependency.ArtifactKey;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public interface ProjectRelease {

    static Builder builder(ReleaseId id) {
        return ProjectReleaseImpl.builder(id);
    }

    public static ProjectRelease create(ReleaseId id, List<ProjectDependency> deps) {
        return ProjectReleaseImpl.create(id, deps);
    }

    ReleaseId id();

    Collection<ProjectDependency> dependencies();

    Collection<String> artifactVersions();

    Collection<String> groupIds();

    interface Builder extends ProjectRelease {

        Builder add(ProjectDependency dep);

        Builder add(Artifact a);

        Builder add(Dependency d);

        boolean includes(ArtifactKey key);

        ProjectRelease build();
    }
}
