package io.quarkus.domino;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.domino.scm.ScmRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Circular release dependency chain
 */
public class CircularReleaseDependency {

    public static CircularReleaseDependency of(List<ScmRevision> chain) {
        return new CircularReleaseDependency(chain);
    }

    private final List<ScmRevision> chain;

    private CircularReleaseDependency(List<ScmRevision> chain) {
        if (chain == null || chain.size() < 2) {
            throw new IllegalArgumentException("Invalid circular release dependency chain " + chain);
        }
        this.chain = List.copyOf(chain);
    }

    public List<ScmRevision> getDependencyChain() {
        return chain;
    }

    /**
     * @deprecated for removal in favor of {@link #getDependencyChain()}
     *
     * @return chain of dependencies that form a loop
     */
    @Deprecated(forRemoval = true)
    public List<ReleaseId> getReleaseDependencyChain() {
        var result = new ArrayList<ReleaseId>(chain.size());
        for (var r : chain) {
            result.add(r);
        }
        return result;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append('(').append(chain.get(0));
        for (int i = 1; i < chain.size(); ++i) {
            sb.append(",").append(chain.get(i));
        }
        return sb.append(')').toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(chain);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CircularReleaseDependency other = (CircularReleaseDependency) obj;
        return Objects.equals(chain, other.chain);
    }
}
