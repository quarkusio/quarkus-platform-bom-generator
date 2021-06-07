package io.quarkus.bom.decomposer.maven.platformgen;

public class PlatformMemberReleaseConfig {

    private String lastBomUpdate;
    private String next;

    void applyOverrides(PlatformMemberReleaseConfig overrides) {
        if (overrides.lastBomUpdate != null) {
            lastBomUpdate = overrides.lastBomUpdate;
        }
        if (overrides.next != null) {
            next = overrides.next;
        }
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    public String getLastDetectedBomUpdate() {
        return lastBomUpdate;
    }

    public void setLastDetectedBomUpdate(String lastBomUpdate) {
        this.lastBomUpdate = lastBomUpdate;
    }
}
