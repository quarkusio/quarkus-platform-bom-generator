package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.platform.RedHatExtensionDependencyCheck;

public class UniversalPlatformConfig {

    private String bom;

    private boolean generatePlatformProperties = true;

    private boolean skipInstall;

    private RedHatExtensionDependencyCheck dependencyCheck;

    public String getBom() {
        return bom;
    }

    public void setBom(String bom) {
        this.bom = bom;
    }

    public boolean isGeneratePlatformProperties() {
        return generatePlatformProperties;
    }

    public void setGeneratePlatformProperties(boolean generatePlatformProperties) {
        this.generatePlatformProperties = generatePlatformProperties;
    }

    public boolean isSkipInstall() {
        return skipInstall;
    }

    public void setSkipInstall(boolean skipInstall) {
        this.skipInstall = skipInstall;
    }

    public RedHatExtensionDependencyCheck getRedHatExtensionDependencyCheck() {
        return dependencyCheck;
    }

    public void setRedHatExtensionDependencyCheck(RedHatExtensionDependencyCheck dependencyCheck) {
        this.dependencyCheck = dependencyCheck;
    }
}
