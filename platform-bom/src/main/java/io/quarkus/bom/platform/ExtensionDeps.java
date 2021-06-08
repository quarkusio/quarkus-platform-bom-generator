package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bootstrap.model.AppArtifactKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExtensionDeps {

    private final AppArtifactKey runtimeKey;
    private final Set<AppArtifactKey> runtimeDeps = new HashSet<>();
    private final Set<AppArtifactKey> deploymentDeps = new HashSet<>();
    private final Map<ReleaseId, ProjectRelease> projectDeps = new HashMap<>();
    private final Map<AppArtifactKey, ExtensionDeps> extensionDeps = new HashMap<>();

    ExtensionDeps(AppArtifactKey runtimeKey) {
        this.runtimeKey = runtimeKey;
    }

    AppArtifactKey key() {
        return runtimeKey;
    }

    void addProjectDep(ProjectRelease r) {
        projectDeps.putIfAbsent(r.id(), r);
    }

    Collection<ProjectRelease> getProjectDeps() {
        return projectDeps.values();
    }

    void addRuntimeDep(AppArtifactKey dep) {
        runtimeDeps.add(dep);
    }

    Collection<AppArtifactKey> getRuntimeDeps() {
        return runtimeDeps;
    }

    boolean isRuntimeDep(AppArtifactKey key) {
        return runtimeDeps.contains(key);
    }

    void addDeploymentDep(AppArtifactKey dep) {
        deploymentDeps.add(dep);
    }

    Collection<AppArtifactKey> getDeploymentDeps() {
        return deploymentDeps;
    }

    void addExtensionDep(ExtensionDeps ext) {
        extensionDeps.put(ext.key(), ext);
    }

    Collection<AppArtifactKey> getExtensionDeps() {
        return extensionDeps.keySet();
    }
}
