package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomTransformer;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

public class ExtensionFilter implements DecomposedBomTransformer {

    private static ExtensionFilter noop;

    static ExtensionFilter getInstance(ArtifactResolver resolver, MessageWriter log, PlatformMember member) {
        if (member.extensionCatalog().isEmpty()) {
            return noop();
        }
        return new ExtensionFilter(resolver, log, member);
    }

    static ExtensionFilter noop() {
        if (noop == null) {
            noop = new ExtensionFilter(null, null, null) {
                @Override
                public DecomposedBom transform(DecomposedBom decomposed) throws BomDecomposerException {
                    return decomposed;
                }
            };
        }
        return noop;
    }

    private final ArtifactResolver resolver;
    private final MessageWriter log;
    private final PlatformMember member;
    private Set<ArtifactKey> filteredOutDeps = Collections.emptySet();

    public ExtensionFilter(ArtifactResolver resolver, MessageWriter log, PlatformMember member) {
        this.resolver = resolver;
        this.log = log;
        this.member = member;
    }

    @Override
    public DecomposedBom transform(DecomposedBom decomposed) throws BomDecomposerException {

        final List<Dependency> allDepList = new ArrayList<>();
        final Map<ArtifactKey, ProjectDependency> allDepMap = new HashMap<>();
        final Map<ArtifactKey, ProjectRelease> releaseMap = new HashMap<>();
        final Map<ReleaseId, ProjectRelease> releases = new HashMap<>();

        for (ProjectRelease r : decomposed.releases()) {
            releases.put(r.id(), r);
            for (ProjectDependency d : r.dependencies()) {
                allDepList.add(d.dependency());
                final ArtifactKey key = d.key();
                allDepMap.put(key, d);
                releaseMap.put(key, r);
            }
        }

        final Map<ArtifactKey, AtomicInteger> depCounter = new HashMap<>();
        final PlatformMemberExtensions memberExt = new PlatformMemberExtensions(member);
        for (ProjectRelease r : decomposed.releases()) {
            for (ProjectDependency d : r.dependencies()) {
                final Artifact a = d.artifact();
                final ArtifactKey extKey = d.key();
                final Path p;
                try {
                    p = resolver.resolve(a).getArtifact().getFile().toPath();
                } catch (Exception e) {
                    log.warn("Failed to resolve " + a);
                    allDepMap.remove(extKey);
                    continue;
                }
                if (!p.getFileName().toString().endsWith(".jar")) {
                    continue;
                }

                String deploymentStr;
                try (FileSystem fs = FileSystems.newFileSystem(p, null)) {
                    final Path propsPath = fs.getPath(BootstrapConstants.DESCRIPTOR_PATH);
                    if (!Files.exists(propsPath)) {
                        continue;
                    }
                    final Properties props = new Properties();
                    try (BufferedReader reader = Files.newBufferedReader(propsPath)) {
                        props.load(reader);
                    }
                    deploymentStr = props.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
                    if (deploymentStr == null) {
                        continue;
                    }
                } catch (IOException e) {
                    throw new BomDecomposerException("Failed to read " + BootstrapConstants.DESCRIPTOR_PATH + " from " + p, e);
                }

                final ArtifactCoords deploymentCoords = ArtifactCoords.fromString(deploymentStr);
                final ExtensionDeps ext = new ExtensionDeps(extKey);
                ext.addDeploymentDep(deploymentCoords.getKey());
                final AtomicInteger count = depCounter.computeIfAbsent(deploymentCoords.getKey(), k -> new AtomicInteger(0));
                if (member.extensionCatalog().contains(extKey)) {
                    count.incrementAndGet();
                }

                allDepMap.remove(deploymentCoords.getKey());
                allDepMap.remove(extKey);

                final AtomicBoolean runtimeCp = new AtomicBoolean(false);
                final DependencyVisitor visitor = new DependencyVisitor() {

                    @Override
                    public boolean visitEnter(DependencyNode node) {
                        if (node.getDependency() == null) {
                            return true;
                        }
                        final Artifact a = node.getDependency().getArtifact();
                        final ArtifactKey key = key(a);
                        depCounter.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                        allDepMap.remove(key);

                        if (runtimeCp.get()) {
                            ext.addRuntimeDep(key);
                        } else {
                            ext.addDeploymentDep(key);
                        }

                        final ProjectRelease r = releaseMap.get(key);
                        if (r != null) {
                            ext.addProjectDep(r);
                            for (ProjectDependency d : r.dependencies()) {
                                allDepMap.remove(d.key());
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean visitLeave(DependencyNode node) {
                        return true;
                    }
                };
                try {
                    resolver.underlyingResolver().collectManagedDependencies(
                            new DefaultArtifact(deploymentCoords.getGroupId(), deploymentCoords.getArtifactId(),
                                    deploymentCoords.getClassifier(), deploymentCoords.getType(),
                                    deploymentCoords.getVersion()),
                            Collections.emptyList(), allDepList, Collections.emptyList(), Collections.emptyList(), "test")
                            .getRoot().accept(visitor);
                } catch (BootstrapMavenException e) {
                    throw new BomDecomposerException("Failed to collect dependencies for " + deploymentCoords, e);
                }
                runtimeCp.set(true);
                try {
                    resolver.underlyingResolver().collectManagedDependencies(a,
                            Collections.emptyList(), allDepList, Collections.emptyList(), Collections.emptyList(), "test")
                            .getRoot().accept(visitor);
                } catch (BootstrapMavenException e) {
                    throw new BomDecomposerException("Failed to collect dependencies for " + a, e);
                }

                memberExt.addExtension(ext);
            }
        }

        for (ExtensionDeps ext : memberExt.getFilteredOutExtensions()) {
            for (ArtifactKey key : ext.getRuntimeDeps()) {
                final AtomicInteger c = depCounter.get(key);
                if (c.get() > 0) {
                    c.decrementAndGet();
                }
            }
            for (ArtifactKey key : ext.getDeploymentDeps()) {
                final AtomicInteger c = depCounter.get(key);
                if (c.get() > 0) {
                    c.decrementAndGet();
                }
            }
        }

        filteredOutDeps = new HashSet<>();
        for (Map.Entry<ArtifactKey, AtomicInteger> e : depCounter.entrySet()) {
            if (e.getValue().get() == 0) {
                filteredOutDeps.add(e.getKey());
            }
        }

        final DecomposedBom.Builder builder = DecomposedBom.builder().bomArtifact(decomposed.bomArtifact());

        for (ProjectDependency d : allDepMap.values()) {
            final ProjectRelease r = releases.remove(d.releaseId());
            if (r != null) {
                addProjectRelease(builder, r);
            }
        }

        for (ArtifactKey extKey : member.extensionCatalog()) {
            final ExtensionDeps ext = memberExt.getExtension(extKey);
            for (ProjectRelease r : ext.getProjectDeps()) {
                if (releases.remove(r.id()) != null) {
                    addProjectRelease(builder, r);
                }
            }
        }

        return builder.build();
    }

    private void addProjectRelease(final DecomposedBom.Builder builder, final ProjectRelease r) {
        final ProjectRelease.Builder prb = ProjectRelease.builder(r.id());
        for (ProjectDependency pd : r.dependencies()) {
            if (!isFilteredOut(pd.key())) {
                prb.add(pd);
            }
        }
        builder.addRelease(prb.build());
    }

    boolean isFilteredOut(ArtifactKey key) {
        return filteredOutDeps.contains(key);
    }

    private static ArtifactKey key(final Artifact a) {
        return new ArtifactKey(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension());
    }
}
