package io.quarkus.bom.decomposer.maven.platformgen;

public class PlatformMemberReleaseConfig {

    private boolean skip;
    private String upcoming;

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public String getUpcoming() {
        return upcoming;
    }

    public void setUpcoming(String upcoming) {
        this.upcoming = upcoming;
    }
}
