package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import io.quarkus.bom.PomSource;
import io.quarkus.bom.resolver.ArtifactNotFoundException;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectModelResolver;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactResult;

public class BomDecomposer {

    private static boolean isParallelProcessing() {
        return Boolean.getBoolean("parallelProcessing");
    }

    public static BomDecomposerConfig config() {
        return new BomDecomposer().new BomDecomposerConfig();
    }

    public class BomDecomposerConfig {

        boolean loadReleaseDetectors = true;

        private BomDecomposerConfig() {
        }

        public BomDecomposerConfig logger(MessageWriter messageWriter) {
            logger = messageWriter;
            return this;
        }

        public BomDecomposerConfig debug() {
            debug = true;
            return this;
        }

        public BomDecomposerConfig mavenArtifactResolver(ArtifactResolver resolver) {
            mvnResolver = resolver;
            return this;
        }

        public BomDecomposerConfig bomFile(Path bom) {

            final MavenArtifactResolver.Builder resolverBuilder = MavenArtifactResolver.builder()
                    .setCurrentProject(bom.normalize().toAbsolutePath().toString());
            if (mvnResolver != null) {
                final MavenArtifactResolver baseResolver = mvnResolver.underlyingResolver();
                resolverBuilder.setRepositorySystem(baseResolver.getSystem())
                        .setRemoteRepositoryManager(baseResolver.getRemoteRepositoryManager());
            }
            MavenArtifactResolver underlyingResolver;
            try {
                underlyingResolver = resolverBuilder.build();
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize Maven artifact resolver for " + bom, e);
            }
            mavenArtifactResolver(
                    ArtifactResolverProvider.get(underlyingResolver,
                            mvnResolver == null ? null : mvnResolver.getBaseDir()));

            final BootstrapMavenContext mvnCtx = underlyingResolver.getMavenContext();
            final LocalProject bomProject = mvnCtx.getCurrentProject();
            bomArtifact = new DefaultArtifact(bomProject.getGroupId(), bomProject.getArtifactId(),
                    ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, bomProject.getVersion());
            bomArtifact = bomArtifact.setFile(bom.toFile());
            bomSource = PomSource.of(bom);
            return this;
        }

        public BomDecomposerConfig bomArtifact(String groupId, String artifactId, String version) {
            return bomArtifact(new DefaultArtifact(groupId, artifactId, ArtifactCoords.DEFAULT_CLASSIFIER,
                    ArtifactCoords.TYPE_POM, version));
        }

        public BomDecomposerConfig bomArtifact(Artifact artifact) {
            bomArtifact = artifact;
            bomSource = PomSource.of(artifact);
            return this;
        }

        public BomDecomposerConfig addReleaseDetector(ReleaseIdDetector releaseDetector) {
            releaseDetectors.add(releaseDetector);
            return this;
        }

        public BomDecomposerConfig checkForUpdates() {
            return transform(new UpdateAvailabilityTransformer(mvnResolver, logger));
        }

        public BomDecomposerConfig transform(DecomposedBomTransformer bomTransformer) {
            transformer = bomTransformer;
            return this;
        }

        public BomDecomposerConfig dependencies(Collection<Dependency> iterator) {
            artifacts = iterator;
            return this;
        }

        public BomDecomposerConfig loadReleaseDetectors(boolean loadReleaseDetectors) {
            this.loadReleaseDetectors = loadReleaseDetectors;
            return this;
        }

        public DecomposedBom decompose() throws BomDecomposerException {
            if (loadReleaseDetectors) {
                ServiceLoader.load(ReleaseIdDetector.class, Thread.currentThread().getContextClassLoader())
                        .forEach(d -> {
                            BomDecomposer.this.logger().debug("Loaded release detector " + d);
                            releaseDetectors.add(d);
                        });
            }
            revisionResolver = new ScmRevisionResolver(artifactResolver(), releaseDetectors);
            return BomDecomposer.this.decompose();
        }
    }

    private BomDecomposer() {
    }

    private MessageWriter logger;
    private boolean debug;
    private Artifact bomArtifact;
    private PomResolver bomSource;
    private Collection<Dependency> artifacts;
    private ArtifactResolver mvnResolver;
    private ProjectModelResolver modelResolver;
    private List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
    private DecomposedBomBuilder decomposedBuilder;
    private DecomposedBomTransformer transformer;
    private ScmRevisionResolver revisionResolver;

    private ArtifactResolver artifactResolver() {
        try {
            return mvnResolver == null
                    ? mvnResolver = ArtifactResolverProvider
                            .get(MavenArtifactResolver.builder().setArtifactTransferLogging(debug).build())
                    : mvnResolver;
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
        }
    }

    private DecomposedBom decompose() throws BomDecomposerException {
        final DecomposedBomBuilder bomBuilder = decomposedBuilder == null ? new DefaultDecomposedBomBuilder()
                : decomposedBuilder;
        bomBuilder.bomArtifact(bomArtifact);
        //bomBuilder.bomSource(PomSource.of(resolve(bomArtifact).getFile().toPath()));
        var artifacts = this.artifacts == null ? bomManagedDeps() : this.artifacts;
        if (isParallelProcessing()) {
            addConcurrently(bomBuilder, artifacts);
        } else {
            for (Dependency dep : artifacts) {
                addDependency(bomBuilder, dep);
            }
        }
        return transformer == null ? bomBuilder.build() : transformer.transform(bomBuilder.build());
    }

    private void addConcurrently(DecomposedBomBuilder bomBuilder, Collection<Dependency> deps) {
        final List<CompletableFuture<?>> tasks = new ArrayList<>(deps.size());
        final Queue<Map.Entry<Dependency, Exception>> failed = new ConcurrentLinkedDeque<>();
        for (var dep : deps) {
            tasks.add(CompletableFuture.runAsync(() -> {
                try {
                    addDependency(bomBuilder, dep);
                } catch (Exception e) {
                    failed.add(Map.entry(dep, e));
                }
            }));
        }
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        if (!failed.isEmpty()) {
            for (var d : failed) {
                logger.error("Failed to process dependency " + d.getKey(), d.getValue());
            }
            throw new RuntimeException("Failed to process dependencies reported above");
        }
    }

    private void addDependency(DecomposedBomBuilder bomBuilder, Dependency dep) throws BomDecomposerException {
        try {
            // filter out dependencies that can't be resolved
            // if an artifact has a classifier we resolve the artifact itself
            // if an artifact does not have a classifier we will try resolving its pom
            validateArtifact(dep.getArtifact());
            ScmRevision revision = resolveRevision(dep.getArtifact());
            bomBuilder.bomDependency(revision, dep);
        } catch (ArtifactNotFoundException e) {
            // there are plenty of BOMs that include artifacts that don't exist
            logger().debug("Failed to resolve %s", dep);
        }
    }

    private ScmRevision resolveRevision(Artifact artifact) throws BomDecomposerException {
        return revisionResolver.resolveRevision(artifact, List.of());
    }

    private void validateArtifact(Artifact artifact) throws BomDecomposerException {
        final String classifier = artifact.getClassifier();
        if (!classifier.isEmpty() &&
                !classifier.equals("sources") &&
                !classifier.equals("javadoc")) {
            resolve(artifact);
        } else if (ArtifactCoords.TYPE_JAR.equals(artifact.getExtension())) {
            final Model model = revisionResolver.readPom(artifact);
            if (ArtifactCoords.TYPE_POM.equals(model.getPackaging())) {
                // if an artifact has type JAR but the packaging is POM then check whether the artifact is resolvable
                try {
                    resolve(artifact);
                } catch (ArtifactNotFoundException e) {
                    final DistributionManagement distr = model.getDistributionManagement();
                    if (distr == null || distr.getRelocation() == null) {
                        // there is no relocation, so it can be removed
                        throw e;
                    }
                    logger().debug("Found relocation for %s", artifact);
                }
            }
        }
    }

    private List<Dependency> bomManagedDeps() {
        return describe(bomArtifact).getManagedDependencies();
    }

    public MessageWriter logger() {
        return logger == null ? logger = MessageWriter.debug() : logger;
    }

    private ArtifactDescriptorResult describe(Artifact artifact) {
        return artifactResolver().describe(artifact);
    }

    private ArtifactResult resolve(Artifact artifact) {
        return artifactResolver().resolve(artifact);
    }

    private ProjectModelResolver getModelResolver() {
        if (modelResolver == null) {
            final MavenArtifactResolver mvn = artifactResolver().underlyingResolver();
            modelResolver = new ProjectModelResolver(mvn.getSession(), new RequestTrace(null), mvn.getSystem(),
                    mvn.getRemoteRepositoryManager(), mvn.getRepositories(),
                    ProjectBuildingRequest.RepositoryMerging.POM_DOMINANT, null);
        }
        return modelResolver;
    }
}
