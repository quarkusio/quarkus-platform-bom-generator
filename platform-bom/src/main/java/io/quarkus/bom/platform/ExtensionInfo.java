package io.quarkus.bom.platform;

import org.eclipse.aether.artifact.Artifact;

class ExtensionInfo {

    private final Artifact runtime;
    private final Artifact deployment;

    ExtensionInfo(Artifact runtime, Artifact deployment) {
        this.runtime = runtime;
        this.deployment = deployment;
    }

    Artifact getRuntime() {
        return runtime;
    }

    Artifact getDeployment() {
        return deployment;
    }
}
