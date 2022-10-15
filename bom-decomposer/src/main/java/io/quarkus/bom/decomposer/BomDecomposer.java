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
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectModelResolver;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class BomDecomposer {

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
            bomArtifact = new DefaultArtifact(bomProject.getGroupId(), bomProject.getArtifactId(), "", "pom",
                    bomProject.getVersion());
            bomArtifact = bomArtifact.setFile(bom.toFile());
            bomSource = PomSource.of(bom);
            return this;
        }

        public BomDecomposerConfig bomArtifact(String groupId, String artifactId, String version) {
            return bomArtifact(new DefaultArtifact(groupId, artifactId, "", "pom", version));
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

        public BomDecomposerConfig dependencies(Iterable<Dependency> iterator) {
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
            releaseIdResolver = new ReleaseIdResolver(artifactResolver(), releaseDetectors);
            return BomDecomposer.this.decompose();
        }
    }

    private BomDecomposer() {
    }

    private MessageWriter logger;
    private boolean debug;
    private Artifact bomArtifact;
    private PomResolver bomSource;
    private Iterable<Dependency> artifacts;
    private ArtifactResolver mvnResolver;
    private ProjectModelResolver modelResolver;
    private List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
    private DecomposedBomBuilder decomposedBuilder;
    private DecomposedBomTransformer transformer;
    private ReleaseIdResolver releaseIdResolver;

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
        final Iterable<Dependency> artifacts = this.artifacts == null ? bomManagedDeps() : this.artifacts;
        for (Dependency dep : artifacts) {
            try {
                // filter out dependencies that can't be resolved
                // if an artifact has a classifier we resolve the artifact itself
                // if an artifact does not have a classifier we will try resolving its pom
                final Artifact artifact = dep.getArtifact();
                final String classifier = artifact.getClassifier();
                if (!classifier.isEmpty() &&
                        !classifier.equals("sources") &&
                        !classifier.equals("javadoc")) {
                    resolve(artifact);
                } else if (ArtifactCoords.TYPE_JAR.equals(artifact.getExtension())
                        && ArtifactCoords.TYPE_POM.equals(releaseIdResolver.model(artifact).getPackaging())) {
                    // if it's not a pom but the packaging in the POM is pom then check whether the artifact is resolvable
                    try {
                        resolve(artifact);
                    } catch (BomDecomposerException | ArtifactNotFoundException e) {
                        // if it's not resolvable then turn it into a POM artifact
                        dep = dep.setArtifact(new DefaultArtifact(artifact.getGroupId(),
                                artifact.getArtifactId(), ArtifactCoords.TYPE_POM, artifact.getVersion()));
                    }
                }
                bomBuilder.bomDependency(releaseIdResolver.releaseId(artifact), dep);
            } catch (BomDecomposerException e) {
                throw e;
            } catch (ArtifactNotFoundException | UnresolvableModelException e) {
                // there are plenty of BOMs that include artifacts that don't exist
                logger().debug("Failed to resolve POM for %s", dep);
            }
        }
        return transformer == null ? bomBuilder.build() : transformer.transform(bomBuilder.build());
    }

    private Iterable<Dependency> bomManagedDeps() throws BomDecomposerException {
        return describe(bomArtifact).getManagedDependencies();
    }

    public MessageWriter logger() {
        return logger == null ? logger = MessageWriter.debug() : logger;
    }

    private ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
        return artifactResolver().describe(artifact);
    }

    private Artifact resolve(Artifact artifact) throws BomDecomposerException {
        return artifactResolver().resolve(artifact).getArtifact();
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
