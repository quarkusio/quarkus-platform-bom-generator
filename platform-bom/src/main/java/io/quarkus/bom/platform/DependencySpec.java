package io.quarkus.bom.platform;

import java.util.Collections;
import java.util.List;

public class DependencySpec {

    private String artifact;
    private String scope;
    private List<String> exclusions = Collections.emptyList();

    public String getArtifact() {
        return artifact;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public List<String> getExclusions() {
        return exclusions;
    }

    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }

    @Override
    public String toString() {
        if (exclusions.isEmpty() && (scope == null || scope.isEmpty())) {
            return artifact;
        }
        final StringBuilder s = new StringBuilder();
        s.append(artifact);
        if (scope != null && !scope.isEmpty()) {
            s.append('(').append(scope).append(')');
        }
        if (!exclusions.isEmpty()) {
            s.append(" exclusions: ").append(exclusions.get(0));
            for (int i = 1; i < exclusions.size(); ++i) {
                s.append(',').append(exclusions.get(i));
            }
        }
        return s.toString();
    }
}
