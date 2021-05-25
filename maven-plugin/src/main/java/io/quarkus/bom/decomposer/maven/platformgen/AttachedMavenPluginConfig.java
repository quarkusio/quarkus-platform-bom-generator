package io.quarkus.bom.decomposer.maven.platformgen;

public class AttachedMavenPluginConfig {

    private String originalPluginCoords;
    private String targetPluginCoords;

    public String getOriginalPluginCoords() {
        return originalPluginCoords;
    }

    public void setOriginalPluginCoords(String originalPluginCoords) {
        this.originalPluginCoords = originalPluginCoords;
    }

    public String getTargetPluginCoords() {
        return targetPluginCoords;
    }

    public void setTargetPluginCoords(String targetPluginCoords) {
        this.targetPluginCoords = targetPluginCoords;
    }
}
