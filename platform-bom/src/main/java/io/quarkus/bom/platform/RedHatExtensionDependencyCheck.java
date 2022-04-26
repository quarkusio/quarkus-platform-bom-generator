package io.quarkus.bom.platform;

public class RedHatExtensionDependencyCheck {

    private String versionPattern;
    private int checkDepth = Integer.MAX_VALUE;
    private boolean enabled = true;

    public String getVersionPattern() {
        return versionPattern;
    }

    public void setVersionPattern(String versionPattern) {
        this.versionPattern = versionPattern;
    }

    public int getCheckDepth() {
        return checkDepth;
    }

    public void setCheckDepth(int checkDepth) {
        this.checkDepth = checkDepth;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
