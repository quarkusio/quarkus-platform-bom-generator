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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.eclipse.aether.repository.RemoteRepository;

public class PurgingDependencyTreeVisitor implements DependencyTreeVisitor {

    private final AtomicLong nodesTotal = new AtomicLong();
    private final AtomicLong uniqueNodesTotal = new AtomicLong();
    private List<VisitedComponentImpl> roots;
    private ArrayDeque<VisitedComponentImpl> branch;
    private Map<ArtifactCoords, List<VisitedComponentImpl>> nodeVariations;

    @Override
    public void beforeAllRoots() {
        nodesTotal.set(0);
        uniqueNodesTotal.set(0);
        roots = new ArrayList<>();
        branch = new ArrayDeque<>();
        nodeVariations = new HashMap<>();
    }

    @Override
    public void afterAllRoots() {
        purge();
    }

    public List<VisitedComponent> getRoots() {
        return new ArrayList<>(roots);
    }

    private void purge() {
        //System.out.println("Roots total: " + roots.size());
        //System.out.println("Nodes total: " + nodesTotal);

        for (VisitedComponentImpl root : roots) {
            // we want to process each tree separately due to possible variations across different trees
            var treeProcessor = newTreeProcessor();
            treeProcessor.addRoot(root);
            treeProcessor.schedule().join();
        }
        nodeVariations = null;
        branch = null;

        if (roots.size() > 1) {
            var ids = new HashMap<String, VisitedComponentImpl>(roots.size());
            var i = roots.iterator();
            while (i.hasNext()) {
                var current = i.next();
                VisitedComponent previous = ids.put(current.getBomRef(), current);
                if (previous != null) {
                    i.remove();
                }
            }
        }
        //System.out.println("Unique roots total: " + roots.size());
        //System.out.println("Unique nodes total: " + uniqueNodesTotal);
    }

    private ParallelTreeProcessor<Long, VisitedComponentImpl, VisitedComponentImpl> newTreeProcessor() {
        return ParallelTreeProcessor.with(new NodeProcessor<Long, VisitedComponentImpl, VisitedComponentImpl>() {
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
                    VisitedComponentImpl currentNode = ctx.getNode();
                    final List<VisitedComponentImpl> variations = nodeVariations.get(currentNode.getArtifactCoords());
                    long processedVariations = 0;
                    if (variations.size() > 1) {
                        for (VisitedComponentImpl variation : variations) {
                            if (variation.getBomRef() == null) {
                                continue;
                            }
                            processedVariations++;
                            if (variation.hasMatchingDirectDeps(currentNode)) {
                                if (currentNode.getParent() == null) {
                                    // root of the tree
                                    currentNode.setBomRef(variation.getBomRef());
                                } else {
                                    currentNode.getParent().addChild(variation);
                                    currentNode = variation;
                                }
                                break;
                            }
                        }
                    }
                    if (currentNode.getBomRef() == null) {
                        uniqueNodesTotal.incrementAndGet();
                        currentNode.initializeBomRef(processedVariations);
                    }
                    return ctx.success(currentNode);
                };
            }
        });
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        roots.add(enterNode(visit));
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        leaveNode();
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
        enterNode(visit);
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
        nodeVariations.computeIfAbsent(visit.getCoords(), k -> new ArrayList<>()).add(current);
        if (parent != null) {
            parent.addChild(current);
        }
        branch.push(current);
        return current;
    }

    private void leaveNode() {
        branch.pop();
    }

    private static class VisitedComponentImpl implements VisitedComponent {
        private final long index;
        private final VisitedComponentImpl parent;
        private final ScmRevision revision;
        private final ArtifactCoords coords;
        private final List<RemoteRepository> repos;
        private final Map<ArtifactCoords, VisitedComponentImpl> children = new HashMap<>();
        private String bomRef;
        private PackageURL purl;

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

        private VisitedComponentImpl getParent() {
            return parent;
        }

        private void addChild(VisitedComponentImpl c) {
            children.put(c.coords, c);
        }

        private boolean hasMatchingDirectDeps(VisitedComponentImpl other) {
            if (!coords.equals(other.coords)) {
                throw new IllegalArgumentException(
                        coords.toCompactCoords() + " does not match " + other.coords.toCompactCoords());
            }
            if (children.size() != other.children.size()) {
                return false;
            }
            for (Map.Entry<ArtifactCoords, VisitedComponentImpl> c : children.entrySet()) {
                var child = c.getValue();
                if (child.bomRef == null) {
                    throw new IllegalStateException(
                            coords + " node has not yet processed dependency on " + child.getArtifactCoords());
                }
                var otherChild = other.children.get(c.getKey());
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
