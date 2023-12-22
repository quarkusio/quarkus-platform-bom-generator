package io.quarkus.domino.inspect;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

public class DependencyTreeRequest {

    /* @formatter:off */
    private static final byte ROOT       = 0b001;
    private static final byte DEPENDENCY = 0b010;
    private static final byte PLUGIN     = 0b100;
    /* @formatter:on */

    public static DependencyTreeRequest ofRoot(Artifact artifact, List<Dependency> constraints,
            Collection<Exclusion> exclusions) {
        return new DependencyTreeRequest(artifact, constraints, exclusions, ROOT);
    }

    public static DependencyTreeRequest ofDependency(Artifact artifact, List<Dependency> constraints,
            Collection<Exclusion> exclusions) {
        return new DependencyTreeRequest(artifact, constraints, exclusions, DEPENDENCY);
    }

    public static DependencyTreeRequest ofPlugin(Artifact artifact) {
        return ofPlugin(artifact, List.of());
    }

    public static DependencyTreeRequest ofPlugin(Artifact artifact, Collection<Exclusion> exclusions) {
        return new DependencyTreeRequest(artifact, List.of(), exclusions, PLUGIN);
    }

    // this is not the best idea but it'll work for now
    private static final AtomicInteger counter = new AtomicInteger();

    private final Integer id;
    private final Artifact root;
    private final List<Dependency> constraints;
    private final Collection<Exclusion> exclusions;
    private final byte type;

    private DependencyTreeRequest(Artifact root, List<Dependency> constraints, Collection<Exclusion> exclusions, byte type) {
        this.root = root;
        this.constraints = constraints;
        this.exclusions = exclusions;
        this.type = type;
        id = counter.incrementAndGet();
    }

    Integer getId() {
        return id;
    }

    public Artifact getArtifact() {
        return root;
    }

    public List<Dependency> getConstraints() {
        return constraints;
    }

    public Collection<Exclusion> getExclusions() {
        return exclusions;
    }

    boolean isRoot() {
        return type == ROOT;
    }

    boolean isDependency() {
        return type == DEPENDENCY;
    }

    boolean isPlugin() {
        return type == PLUGIN;
    }
}
