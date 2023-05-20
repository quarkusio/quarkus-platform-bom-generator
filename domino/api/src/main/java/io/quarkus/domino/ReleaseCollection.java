package io.quarkus.domino;

import io.quarkus.bom.decomposer.ReleaseId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReleaseCollection implements Iterable<ReleaseRepo> {

    static List<ReleaseRepo> sort(Collection<ReleaseRepo> releaseRepos) {
        final int codeReposTotal = releaseRepos.size();
        final List<ReleaseRepo> sorted = new ArrayList<>(codeReposTotal);
        final Set<ReleaseId> processedRepos = new HashSet<>(codeReposTotal);
        for (ReleaseRepo r : releaseRepos) {
            if (r.isRoot()) {
                sort(r, processedRepos, sorted);
            }
        }
        return sorted;
    }

    private static void sort(ReleaseRepo repo, Set<ReleaseId> processed, List<ReleaseRepo> sorted) {
        if (!processed.add(repo.id)) {
            return;
        }
        for (ReleaseRepo d : repo.dependencies.values()) {
            sort(d, processed, sorted);
        }
        sorted.add(repo);
    }

    static Collection<CircularReleaseDependency> detectCircularDependencies(Collection<ReleaseRepo> releaseRepos) {
        final Map<Set<ReleaseId>, CircularReleaseDependency> circularDeps = new HashMap<>();
        for (ReleaseRepo r : releaseRepos) {
            final List<ReleaseId> chain = new ArrayList<>();
            detectCircularDeps(r, chain, circularDeps);
        }
        return circularDeps.values();
    }

    private static void detectCircularDeps(ReleaseRepo r, List<ReleaseId> chain,
            Map<Set<ReleaseId>, CircularReleaseDependency> circularDeps) {
        final int i = chain.indexOf(r.id);
        if (i >= 0) {
            final List<ReleaseId> loop = new ArrayList<>(chain.size() - i + 1);
            for (int j = i; j < chain.size(); ++j) {
                loop.add(chain.get(j));
            }
            loop.add(r.id);
            circularDeps.computeIfAbsent(new HashSet<>(loop), k -> CircularReleaseDependency.of(loop));
            return;
        }
        chain.add(r.id);
        for (ReleaseRepo d : r.dependencies.values()) {
            detectCircularDeps(d, chain, circularDeps);
        }
        chain.remove(chain.size() - 1);
    }

    public static ReleaseCollection of(Collection<ReleaseRepo> releaseRepos) {
        return new ReleaseCollection(releaseRepos);
    }

    private final Collection<ReleaseRepo> releases;

    private ReleaseCollection(Collection<ReleaseRepo> releases) {
        this.releases = releases;
    }

    /**
     * Returns a new collection of releases sorted according to their dependencies.
     * 
     * @return new collection of releases sorted according to their dependencies
     */
    public ReleaseCollection sort() {
        return new ReleaseCollection(sort(releases));
    }

    /**
     * Detects and returns circular release dependencies, if any found.
     * 
     * @return circular release dependencies, if any found
     */
    public Collection<CircularReleaseDependency> getCircularDependencies() {
        return detectCircularDependencies(releases);
    }

    @Override
    public Iterator<ReleaseRepo> iterator() {
        return releases.iterator();
    }

    public boolean isEmpty() {
        return releases.isEmpty();
    }

    public int size() {
        return releases.size();
    }

    Collection<ReleaseRepo> getReleases() {
        return releases;
    }
}
