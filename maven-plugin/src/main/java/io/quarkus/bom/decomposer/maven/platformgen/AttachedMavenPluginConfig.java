package io.quarkus.bom.decomposer.maven.platformgen;

public class AttachedMavenPluginConfig {

    private String originalPluginCoords;
    private String targetPluginCoords;
    private Boolean importSources;
    private Boolean flattenPom;

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
        return importSources == null ? true : importSources.booleanValue();
    }

    public void setFlattenPom(boolean flattenPom) {
        this.flattenPom = flattenPom;
    }

    public boolean isFlattenPom() {
        return flattenPom == null ? true : flattenPom.booleanValue();
    }
}
