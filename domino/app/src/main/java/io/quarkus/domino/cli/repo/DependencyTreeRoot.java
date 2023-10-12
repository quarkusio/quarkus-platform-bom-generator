package io.quarkus.domino.cli.repo;

import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

public class DependencyTreeRoot {

    private final Artifact root;
    private final List<Dependency> constraints;
    private final Collection<Exclusion> exclusions;

    public DependencyTreeRoot(Artifact root, List<Dependency> constraints, Collection<Exclusion> exclusions) {
        this.root = root;
        this.constraints = constraints;
        this.exclusions = exclusions;
    }

    String getId() {
        return root.toString();
    }

    Artifact getArtifact() {
        return root;
    }

    List<Dependency> getConstraints() {
        return constraints;
    }

    Collection<Exclusion> getExclusions() {
        return exclusions;
    }
}
