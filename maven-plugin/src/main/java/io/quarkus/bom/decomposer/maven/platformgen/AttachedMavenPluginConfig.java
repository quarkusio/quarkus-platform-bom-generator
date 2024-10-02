package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.platform.PlatformMemberTestConfig;

public class AttachedMavenPluginConfig {

    private String originalPluginCoords;
    private String targetPluginCoords;
    private Boolean importSources;
    private Boolean flattenPom;
    private PlatformMemberTestConfig test;

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

    public void setImportSources(boolean importSources) {
        this.importSources = importSources;
    }

    public boolean isImportSources() {
        return importSources == null || importSources;
    }

    public void setFlattenPom(boolean flattenPom) {
        this.flattenPom = flattenPom;
    }

    public boolean isFlattenPom() {
        return flattenPom == null || flattenPom;
    }

    public PlatformMemberTestConfig getTest() {
        return test;
    }

    public void setTest(PlatformMemberTestConfig test) {
        this.test = test;
    }
}
