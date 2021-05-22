package io.quarkus.bom.decomposer.maven.platformgen;

public class PlatformMemberReleaseConfig {

    private boolean skip;
    private String next;

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }
}
