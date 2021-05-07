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
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class BomDecomposer {

    public static BomDecomposerConfig config() {
        return new BomDecomposer().new BomDecomposerConfig();
    }

    public class BomDecomposerConfig {

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

        public DecomposedBom decompose() throws BomDecomposerException {
            ServiceLoader.load(ReleaseIdDetector.class, Thread.currentThread().getContextClassLoader()).forEach(d -> {
                BomDecomposer.this.logger().debug("Loaded release detector " + d);
                releaseDetectors.add(d);
            });
            return BomDecomposer.this.decompose();
        }
    }

    public static DecomposedBom decompose(String groupId, String artifactId, String version)
            throws BomDecomposerException {
        final BomDecomposer decomposer = new BomDecomposer();
        decomposer.bomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);
        decomposer.bomSource = PomSource.of(decomposer.bomArtifact);
        return decomposer.decompose();
    }

    private BomDecomposer() {
    }

    private MessageWriter logger;
    private boolean debug;
    private Artifact bomArtifact;
    private PomResolver bomSource;
    private Iterable<Dependency> artifacts;
    private ArtifactResolver mvnResolver;
    private List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
    private DecomposedBomBuilder decomposedBuilder;
    private DecomposedBomTransformer transformer;

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
                bomBuilder.bomDependency(releaseId(dep.getArtifact()), dep);
            } catch (BomDecomposerException e) {
                throw e;
            } catch (ArtifactNotFoundException e) {
                // there are plenty of BOMs that include artifacts that don't exist
                logger().debug("Failed to resolve POM for %s", dep);
            }
        }
        return transformer == null ? bomBuilder.build() : transformer.transform(bomBuilder.build());
    }

    private Iterable<Dependency> bomManagedDeps() throws BomDecomposerException {
        return describe(bomArtifact).getManagedDependencies();
    }

    private ReleaseId releaseId(Artifact artifact) throws BomDecomposerException {
        for (ReleaseIdDetector releaseDetector : releaseDetectors) {
            final ReleaseId releaseId = releaseDetector.detectReleaseId(this, artifact);
            if (releaseId != null) {
                return releaseId;
            }
        }
        Model model = model(artifact);
        Model tmp;
        while ((tmp = workspaceParent(model)) != null) {
            model = tmp;
        }
        return ReleaseIdFactory.forModel(model);
    }

    private Model workspaceParent(Model model) throws BomDecomposerException {
        if (model.getParent() == null) {
            return null;
        }

        final Model parentModel = model(Util.parentArtifact(model));

        if (Util.getScmOrigin(model) != null) {
            return Util.getScmOrigin(model).equals(Util.getScmOrigin(parentModel))
                    && Util.getScmTag(model).equals(Util.getScmTag(parentModel)) ? parentModel : null;
        }

        if (model.getParent().getRelativePath().isEmpty()) {
            return null;
        }

        if (model.getVersion() == null || !"../pom.xml".equals(model.getParent().getRelativePath())
                || ModelUtils.getGroupId(parentModel).equals(ModelUtils.getGroupId(model))
                        && ModelUtils.getVersion(parentModel).equals(ModelUtils.getVersion(model))) {
            return parentModel;
        }

        if (parentModel.getModules().isEmpty()) {
            return null;
        }
        for (String path : parentModel.getModules()) {
            final String dirName = Paths.get(path).getFileName().toString();
            if (model.getArtifactId().contains(dirName)) {
                return parentModel;
            }
        }
        return null;
    }

    public MessageWriter logger() {
        return logger == null ? logger = MessageWriter.debug() : logger;
    }

    public Model model(Artifact artifact) throws BomDecomposerException {
        return Util.model(resolve(Util.pom(artifact)).getFile());
    }

    private ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
        return artifactResolver().describe(artifact);
    }

    private Artifact resolve(Artifact artifact) throws BomDecomposerException {
        return artifactResolver().resolve(artifact).getArtifact();
    }
}
