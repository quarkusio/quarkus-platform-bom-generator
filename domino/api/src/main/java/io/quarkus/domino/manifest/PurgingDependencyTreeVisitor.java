package io.quarkus.domino.manifest;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.domino.DependencyTreeVisitor;
import io.quarkus.domino.processor.ExecutionContext;
import io.quarkus.domino.processor.NodeProcessor;
import io.quarkus.domino.processor.ParallelTreeProcessor;
import io.quarkus.domino.processor.TaskResult;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.logging.Logger;

public class PurgingDependencyTreeVisitor implements DependencyTreeVisitor {

    private static final Logger log = Logger.getLogger(PurgingDependencyTreeVisitor.class);

    private final AtomicLong nodesTotal = new AtomicLong();
    private final AtomicLong uniqueNodesTotal = new AtomicLong();
    private List<VisitedComponentImpl> roots;
    private ArrayDeque<VisitedComponentImpl> branch;
    private Map<ArtifactCoords, ComponentVariations> nodeVariations;
    private Map<ArtifactCoords, VisitedComponentImpl> treeComponents;

    @Override
    public void beforeAllRoots() {
        nodesTotal.set(0);
        uniqueNodesTotal.set(0);
        roots = new ArrayList<>();
        branch = new ArrayDeque<>();
        nodeVariations = new ConcurrentHashMap<>();
    }

    @Override
    public void afterAllRoots() {
        purge();
    }

    List<VisitedComponent> getRoots() {
        return new ArrayList<>(roots);
    }

    private void purge() {
        //log.infof("Roots total: %s", roots.size());
        //log.infof("Nodes total: %s", nodesTotal);

        var treeProcessor = newTreeProcessor();
        for (VisitedComponentImpl root : roots) {
            // we want to process each tree separately due to possible variations across different trees
            treeProcessor.addRoot(root);
            var results = treeProcessor.schedule().join();
            boolean failures = false;
            for (var result : results) {
                if (result.isFailure()) {
                    failures = true;
                    log.error("Failed to process " + result.getNode().getArtifactCoords(), result.getException());
                }
            }
            if (failures) {
                throw new RuntimeException(
                        "Failed to record dependency graph, see the errors logged above for more detailed information");
            }
        }
        nodeVariations = null;
        branch = null;

        if (roots.size() > 1) {
            var ids = new HashMap<String, VisitedComponentImpl>(roots.size());
            var i = roots.iterator();
            while (i.hasNext()) {
                var current = i.next();
                var previous = ids.put(current.getBomRef(), current);
                if (previous != null) {
                    i.remove();
                }
            }
        }
        //log.infof("Unique roots total: %s", roots.size());
        //log.infof("Unique nodes total: %s", uniqueNodesTotal);
    }

    private ParallelTreeProcessor<Long, VisitedComponentImpl, VisitedComponentImpl> newTreeProcessor() {
        return ParallelTreeProcessor.with(new NodeProcessor<>() {
            @Override
            public Long getNodeId(VisitedComponentImpl node) {
                return node.getIndex();
            }

            @Override
            public Iterable<VisitedComponentImpl> getChildren(VisitedComponentImpl node) {
                return node.children.values();
            }

            @Override
            public Function<ExecutionContext<Long, VisitedComponentImpl, VisitedComponentImpl>, TaskResult<Long, VisitedComponentImpl, VisitedComponentImpl>> createFunction() {
                return ctx -> {
                    final VisitedComponentImpl currentNode = ctx.getNode();
                    return ctx.success(nodeVariations.get(currentNode.getArtifactCoords()).setBomRef(currentNode));
                };
            }
        });
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        treeComponents = new HashMap<>();
        roots.add(enterNode(visit));
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        leaveNode();
        for (var c : treeComponents.values()) {
            c.resolveLinkedDependencies(treeComponents);
        }
        treeComponents = null;
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
        enterNode(visit);
    }

    @Override
    public void linkDependency(ArtifactCoords coords) {
        var parent = branch.peek();
        if (parent != null) {
            parent.linkDependency(coords);
        }
    }

    @Override
    public void leaveDependency(DependencyVisit visit) {
        leaveNode();
    }

    @Override
    public void enterParentPom(DependencyVisit visit) {
    }

    @Override
    public void leaveParentPom(DependencyVisit visit) {
    }

    @Override
    public void enterBomImport(DependencyVisit visit) {
    }

    @Override
    public void leaveBomImport(DependencyVisit visit) {
    }

    private VisitedComponentImpl enterNode(DependencyVisit visit) {
        var parent = branch.peek();
        var current = new VisitedComponentImpl(nodesTotal.getAndIncrement(), parent, visit);
        nodeVariations.computeIfAbsent(visit.getCoords(), k -> new ComponentVariations()).add(current);
        if (parent != null) {
            parent.addChild(current);
        }
        branch.push(current);
        treeComponents.put(visit.getCoords(), current);
        return current;
    }

    private void leaveNode() {
        branch.pop();
    }

    private class VisitedComponentImpl implements VisitedComponent {
        private final long index;
        private final VisitedComponentImpl parent;
        private final ScmRevision revision;
        private final ArtifactCoords coords;
        private final List<RemoteRepository> repos;
        private final Map<ArtifactCoords, VisitedComponentImpl> children = new ConcurrentHashMap<>();
        private List<ArtifactCoords> linkedDeps;
        private List<VisitedComponentImpl> linkedParents;
        private String bomRef;
        private PackageURL purl;
        private boolean purged;

        private VisitedComponentImpl(long index, VisitedComponentImpl parent, DependencyVisit visit) {
            this.index = index;
            this.parent = parent;
            this.revision = visit.getRevision();
            this.coords = visit.getCoords();
            this.repos = visit.getRepositories();
        }

        private long getIndex() {
            return index;
        }

        private boolean isRoot() {
            return parent == null;
        }

        private void addChild(VisitedComponentImpl c) {
            children.put(c.coords, c);
        }

        private void linkDependency(ArtifactCoords coords) {
            if (linkedDeps == null) {
                linkedDeps = new ArrayList<>();
            }
            linkedDeps.add(coords);
        }

        private void linkParent(VisitedComponentImpl parent) {
            if (linkedParents == null) {
                linkedParents = new ArrayList<>();
            }
            linkedParents.add(parent);
        }

        private void resolveLinkedDependencies(Map<ArtifactCoords, VisitedComponentImpl> treeComponents) {
            if (linkedDeps != null) {
                log.debugf("Resolving linked dependencies of %s", coords);
                // check for circular dependencies
                for (var linked : linkedDeps) {
                    if (isCyclicDependency(linked)) {
                        log.debugf("- %s skipped to avoid a circular dependency", linked);
                        return;
                    }
                }
                for (var linked : linkedDeps) {
                    var c = treeComponents.get(linked);
                    if (c == null) {
                        throw new IllegalStateException("Failed to resolve linked dependency " + linked.toCompactCoords()
                                + " of " + this.coords.toCompactCoords() + " among " + treeComponents.keySet());
                    }
                    log.debugf("- %s", c.coords);
                    addChild(c);
                    c.linkParent(this);
                }
                linkedDeps = null;
            }
        }

        private boolean isCyclicDependency(ArtifactCoords coords) {
            return isSameGav(this.coords, coords) || parent != null && parent.isCyclicDependency(coords);
        }

        private void swap(VisitedComponentImpl other) {
            if (!coords.equals(other.coords)) {
                throw new IllegalArgumentException("Expected " + coords + " but got " + other.coords);
            }
            if (parent != null) {
                parent.addChild(other);
            }
            if (linkedParents != null) {
                for (var p : linkedParents) {
                    p.addChild(other);
                }
            }
            purged = true;
        }

        private boolean hasMatchingDirectDeps(VisitedComponentImpl other) {
            if (!coords.equals(other.coords)) {
                throw new IllegalArgumentException(
                        coords.toCompactCoords() + " does not match " + other.coords.toCompactCoords());
            }
            if (children.size() != other.children.size()) {
                return false;
            }
            for (var child : children.values()) {
                if (child.bomRef == null) {
                    throw new IllegalStateException(
                            coords + " node has not yet processed dependency on " + child.getArtifactCoords());
                }
                var otherChild = other.children.get(child.getArtifactCoords());
                if (otherChild == null) {
                    return false;
                }
                if (otherChild.bomRef == null) {
                    throw new IllegalStateException(
                            other.coords + " node has not yet processed dependency on " + otherChild.getArtifactCoords());
                }
                if (!child.bomRef.equals(otherChild.bomRef)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ScmRevision getRevision() {
            return revision;
        }

        @Override
        public ArtifactCoords getArtifactCoords() {
            return coords;
        }

        @Override
        public List<RemoteRepository> getRepositories() {
            return repos;
        }

        @Override
        public List<VisitedComponent> getDependencies() {
            return new ArrayList<>(children.values());
        }

        @Override
        public PackageURL getPurl() {
            return purl == null ? purl = PurgingDependencyTreeVisitor.getPurl(coords) : purl;
        }

        @Override
        public String getBomRef() {
            return bomRef;
        }

        private void setBomRef(String bomRef) {
            this.bomRef = bomRef;
        }

        private void initializeBomRef(long processedVariations) {
            if (processedVariations == 0) {
                bomRef = getPurl().toString();
                return;
            }
            var sb = new StringBuilder();
            String[] parts = coords.getGroupId().split("\\.");
            sb.append(parts[0].charAt(0));
            for (int i = 1; i < parts.length; ++i) {
                sb.append('.').append(parts[i].charAt(0));
            }
            sb.append(':').append(coords.getArtifactId()).append(':');
            if (!coords.getClassifier().isEmpty()) {
                sb.append(coords.getClassifier()).append(':');
            }
            if (!coords.getType().equals(ArtifactCoords.TYPE_JAR)) {
                sb.append(coords.getType()).append(':');
            }
            sb.append(coords.getVersion()).append('#').append(processedVariations);
            bomRef = sb.toString();
        }

        @Override
        public String toString() {
            return coords.toString();
        }
    }

    private class ComponentVariations {
        private final List<VisitedComponentImpl> variations = new ArrayList<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        private void add(VisitedComponentImpl variation) {
            variations.add(variation);
        }

        private VisitedComponentImpl setBomRef(VisitedComponentImpl currentNode) {
            if (currentNode.bomRef != null) {
                return currentNode;
            }
            lock.readLock().lock();
            try {
                int processedVariations = 0;
                if (variations.size() > 1) {
                    for (var variation : variations) {
                        if (variation.purged || variation.bomRef == null) {
                            continue;
                        }
                        processedVariations++;
                        if (variation.hasMatchingDirectDeps(currentNode)) {
                            if (currentNode.isRoot()) {
                                currentNode.setBomRef(variation.getBomRef());
                            } else {
                                currentNode.swap(variation);
                                currentNode = variation;
                            }
                            break;
                        }
                    }
                }
                if (currentNode.bomRef == null) {
                    uniqueNodesTotal.incrementAndGet();
                    currentNode.initializeBomRef(processedVariations);
                }
            } finally {
                lock.readLock().unlock();
            }
            return currentNode;
        }
    }

    private static boolean isSameGav(ArtifactCoords c1, ArtifactCoords c2) {
        return c1.getArtifactId().equals(c2.getArtifactId())
                && c1.getVersion().equals(c2.getVersion())
                && c1.getGroupId().equals(c2.getGroupId());
    }

    static PackageURL getPurl(ArtifactCoords coords) {
        final TreeMap<String, String> qualifiers = new TreeMap<>();
        qualifiers.put("type", coords.getType());
        if (!coords.getClassifier().isEmpty()) {
            qualifiers.put("classifier", coords.getClassifier());
        }
        try {
            return new PackageURL(PackageURL.StandardTypes.MAVEN,
                    coords.getGroupId(),
                    coords.getArtifactId(),
                    coords.getVersion(),
                    qualifiers, null);
        } catch (MalformedPackageURLException e) {
            throw new RuntimeException("Failed to generate Purl for " + coords.toCompactCoords(), e);
        }
    }
}
