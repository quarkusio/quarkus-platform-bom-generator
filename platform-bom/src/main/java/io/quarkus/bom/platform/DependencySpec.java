package io.quarkus.bom.platform;

import java.util.Collections;
import java.util.List;

public class DependencySpec {

    private String artifact;
    private List<String> exclusions = Collections.emptyList();

    public String getArtifact() {
        return artifact;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    public List<String> getExclusions() {
        return exclusions;
    }

    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    @Override
    public String toString() {
        if (exclusions.isEmpty()) {
            return artifact;
        }
        final StringBuilder s = new StringBuilder();
        s.append(artifact).append(" exclusions: ").append(exclusions.get(0));
        for (int i = 1; i < exclusions.size(); ++i) {
            s.append(',').append(exclusions.get(i));
        }
        return s.toString();
    }
}
