package io.quarkus.domino;

import io.quarkus.domino.scm.ScmRevision;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class ReleaseCollection implements Iterable<ReleaseRepo> {

    static List<ReleaseRepo> filter(Collection<ReleaseRepo> repos, ArtifactSet artifactSelector) {
        final List<ReleaseRepo> result = new ArrayList<>(repos.size());
        for (var repo : repos) {
            repo.getArtifacts().keySet().removeIf(artifactCoords -> !artifactSelector.contains(artifactCoords));
            if (!repo.getArtifacts().isEmpty()) {
                result.add(repo);
            }
        }
        return result;
    }

    static List<ReleaseRepo> sort(Collection<ReleaseRepo> releaseRepos) {
        final int codeReposTotal = releaseRepos.size();
        final List<ReleaseRepo> sorted = new ArrayList<>(codeReposTotal);
        final Set<ScmRevision> processedRepos = new HashSet<>(codeReposTotal);
        for (ReleaseRepo r : releaseRepos) {
            if (r.isRoot()) {
                sort(r, processedRepos, sorted);
            }
        }
        return sorted;
    }

    private static void sort(ReleaseRepo repo, Set<ScmRevision> processed, List<ReleaseRepo> sorted) {
        if (!processed.add(repo.revision)) {
            return;
        }
        for (ReleaseRepo d : repo.dependencies.values()) {
            sort(d, processed, sorted);
        }
        sorted.add(repo);
    }

    static Collection<CircularReleaseDependency> detectCircularDependencies(Collection<ReleaseRepo> releaseRepos) {
        final Map<Set<ScmRevision>, CircularReleaseDependency> circularDeps = new HashMap<>();
        for (ReleaseRepo r : releaseRepos) {
            final List<ScmRevision> chain = new ArrayList<>();
            detectCircularDeps(r, chain, circularDeps);
        }
        return circularDeps.values();
    }

    private static void detectCircularDeps(ReleaseRepo r, List<ScmRevision> chain,
            Map<Set<ScmRevision>, CircularReleaseDependency> circularDeps) {
        final int i = chain.indexOf(r.revision);
        if (i >= 0) {
            final List<ScmRevision> loop = new ArrayList<>(chain.size() - i + 1);
            for (int j = i; j < chain.size(); ++j) {
                loop.add(chain.get(j));
            }
            loop.add(r.revision);
            circularDeps.computeIfAbsent(new HashSet<>(loop), k -> CircularReleaseDependency.of(loop));
            return;
        }
        chain.add(r.revision);
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

    public Iterable<ReleaseRepo> getRootReleaseRepos() {
        return new Iterable<ReleaseRepo>() {
            @Override
            public Iterator<ReleaseRepo> iterator() {
                return new Iterator<ReleaseRepo>() {

                    final Iterator<ReleaseRepo> i = releases.iterator();
                    ReleaseRepo next;

                    @Override
                    public boolean hasNext() {
                        if (next == null) {
                            while (i.hasNext()) {
                                var n = i.next();
                                if (n.isRoot()) {
                                    next = n;
                                    break;
                                }
                            }
                        }
                        return next != null;
                    }

                    @Override
                    public ReleaseRepo next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        var n = next;
                        next = null;
                        return n;
                    }
                };
            }
        };
    }

    Collection<ReleaseRepo> getReleases() {
        return releases;
    }
}
