package io.quarkus.maven.project;

/**
 * Represents an "integrates" metadata entry in a Quarkus extension descriptor.
 * Used to declare which external libraries the extension integrates with.
 */
public class ExtensionIntegrates {

    private final String name;
    private final String artifact;
    private final String version;

    public ExtensionIntegrates(String name, String artifact, String version) {
        this.name = name;
        this.artifact = artifact;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getVersion() {
        return version;
    }
}
