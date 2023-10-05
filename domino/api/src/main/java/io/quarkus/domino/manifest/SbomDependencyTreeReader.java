package io.quarkus.domino.manifest;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.domino.DependencyTreeVisitor;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Mainly for verification purposes.
 */
class SbomDependencyTreeReader {

    static List<TreeNode> readTrees(Bom bom, List<String> rootBomRefs) {
        var bomData = new BomData(bom);
        final TreeRecorder treeRecorder = new TreeRecorder();
        treeRecorder.beforeAllRoots();

        for (Component c : bom.getComponents()) {
            if (rootBomRefs.contains(c.getBomRef())) {
                readTree(c, bomData, treeRecorder);
            }
        }

        treeRecorder.afterAllRoots();
        return treeRecorder.getRoots();
    }

    private static void readTree(Component root, BomData bomData, TreeRecorder treeRecorder) {
        var rootVisit = DepVisit.of(root);
        treeRecorder.enterRootArtifact(rootVisit);
        readDeps(rootVisit, bomData, treeRecorder);
        treeRecorder.leaveRootArtifact(rootVisit);
    }

    private static void readDeps(DepVisit visit, BomData bomData, TreeRecorder treeRecorder) {
        var dep = bomData.getDependency(visit.c.getBomRef());
        if (dep != null) {
            for (Dependency d : dep.getDependencies()) {
                var depVisit = DepVisit.of(bomData.getComponent(d.getRef()));
                treeRecorder.enterDependency(depVisit);
                readDeps(depVisit, bomData, treeRecorder);
                treeRecorder.leaveDependency(depVisit);
            }
        }
    }

    private static class BomData {
        final Map<String, Component> components;
        final Map<String, Dependency> dependencies;

        private BomData(Bom bom) {
            components = new HashMap<>(bom.getComponents().size());
            bom.getComponents().forEach(c -> components.put(c.getBomRef(), c));
            dependencies = new HashMap<>(bom.getDependencies().size());
            bom.getDependencies().forEach(d -> dependencies.put(d.getRef(), d));
        }

        Dependency getDependency(String ref) {
            return dependencies.get(ref);
        }

        Component getComponent(String ref) {
            var c = components.get(ref);
            if (c == null) {
                throw new IllegalArgumentException("No component found for bom-ref " + ref);
            }
            return c;
        }
    }

    private static class DepVisit implements DependencyTreeVisitor.DependencyVisit {

        static DepVisit of(Component c) {
            return new DepVisit(c);
        }

        private final Component c;
        private final ArtifactCoords coords;

        private DepVisit(Component c) {
            this.c = c;
            final PackageURL purl;
            try {
                purl = new PackageURL(c.getPurl());
            } catch (MalformedPackageURLException e) {
                throw new RuntimeException("Bad PURL " + c.getPurl(), e);
            }
            purl.getQualifiers().getOrDefault("type", ArtifactCoords.TYPE_JAR);
            purl.getQualifiers().getOrDefault("classifier", ArtifactCoords.DEFAULT_CLASSIFIER);
            var coords = ArtifactCoords.of(c.getGroup(), c.getName(),
                    purl.getQualifiers().getOrDefault("classifier", ArtifactCoords.DEFAULT_CLASSIFIER),
                    purl.getQualifiers().getOrDefault("type", ArtifactCoords.TYPE_JAR),
                    c.getVersion());
            this.coords = coords;
        }

        @Override
        public ScmRevision getRevision() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArtifactCoords getCoords() {
            return coords;
        }

        @Override
        public List<RemoteRepository> getRepositories() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isManaged() {
            throw new UnsupportedOperationException();
        }
    }
}
