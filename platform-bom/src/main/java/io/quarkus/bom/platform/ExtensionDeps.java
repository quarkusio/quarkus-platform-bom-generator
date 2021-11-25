package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.maven.ArtifactKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExtensionDeps {

    private final ArtifactKey runtimeKey;
    private final Set<ArtifactKey> runtimeDeps = new HashSet<>();
    private final Set<ArtifactKey> deploymentDeps = new HashSet<>();
    private final Map<ReleaseId, ProjectRelease> projectDeps = new HashMap<>();
    private final Map<ArtifactKey, ExtensionDeps> extensionDeps = new HashMap<>();

    ExtensionDeps(ArtifactKey runtimeKey) {
        this.runtimeKey = runtimeKey;
    }

    ArtifactKey key() {
        return runtimeKey;
    }

    void addProjectDep(ProjectRelease r) {
        projectDeps.putIfAbsent(r.id(), r);
    }

    Collection<ProjectRelease> getProjectDeps() {
        return projectDeps.values();
    }

    void addRuntimeDep(ArtifactKey dep) {
        runtimeDeps.add(dep);
    }

    Collection<ArtifactKey> getRuntimeDeps() {
        return runtimeDeps;
    }

    boolean isRuntimeDep(ArtifactKey key) {
        return runtimeDeps.contains(key);
    }

    void addDeploymentDep(ArtifactKey dep) {
        deploymentDeps.add(dep);
    }

    Collection<ArtifactKey> getDeploymentDeps() {
        return deploymentDeps;
    }

    void addExtensionDep(ExtensionDeps ext) {
        extensionDeps.put(ext.key(), ext);
    }

    Collection<ArtifactKey> getExtensionDeps() {
        return extensionDeps.keySet();
    }
}
