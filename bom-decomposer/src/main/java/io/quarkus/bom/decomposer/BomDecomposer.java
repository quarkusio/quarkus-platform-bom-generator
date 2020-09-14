package io.quarkus.bom.decomposer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bom.PomResolver;
import io.quarkus.bom.PomSource;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

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

		public BomDecomposerConfig mavenArtifactResolver(MavenArtifactResolver resolver) {
			mvnResolver = resolver;
			return this;
		}

		public BomDecomposerConfig bomFile(Path bom) {
			BootstrapMavenContext mvnCtx;
			try {
				mvnCtx = new BootstrapMavenContext(BootstrapMavenContext.config().setCurrentProject(bom.normalize().toAbsolutePath().toString()));
			} catch (BootstrapMavenException e) {
				throw new RuntimeException("Failed to initialize bootstrap Maven context", e);
			}
			final LocalProject bomProject = mvnCtx.getCurrentProject();
			try {
				mavenArtifactResolver(new MavenArtifactResolver(mvnCtx));
			} catch (BootstrapMavenException e) {
				throw new RuntimeException("Failed to initialize Maven artifact resolver for " + bom, e);
			}
			bomArtifact = new DefaultArtifact(bomProject.getGroupId(), bomProject.getArtifactId(), "", "pom", bomProject.getVersion());
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
			return transform(new UpdateAvailabilityTransformer());
		}

		public BomDecomposerConfig transform(DecomposedBomTransformer bomTransformer) {
			transformer = bomTransformer;
			return this;
		}

		public BomDecomposerConfig artifacts(Iterable<Artifact> iterator) {
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
	private Iterable<Artifact> artifacts;
	private MavenArtifactResolver mvnResolver;
	private List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
	private DecomposedBomBuilder decomposedBuilder;
	private DecomposedBomTransformer transformer;

	private MavenArtifactResolver artifactResolver() throws BomDecomposerException {
		try {
			return mvnResolver == null ? mvnResolver = new MavenArtifactResolver(new BootstrapMavenContext(
					BootstrapMavenContext.config().setArtifactTransferLogging(debug)))
					: mvnResolver;
		} catch (AppModelResolverException e) {
			throw new BomDecomposerException("Failed to initialize Maven artifact resolver", e);
		}
	}

	private DecomposedBom decompose() throws BomDecomposerException {
		final DecomposedBomBuilder bomBuilder = decomposedBuilder == null ? new DefaultDecomposedBomBuilder() : decomposedBuilder;
		bomBuilder.bomArtifact(bomArtifact);
		final Iterable<Artifact> artifacts = this.artifacts == null ? bomArtifacts() : this.artifacts;
		for (Artifact dep : artifacts) {
			try {
				bomBuilder.bomDependency(releaseId(dep), dep);
			} catch (BomDecomposerException e) {
				if (e.getCause() instanceof AppModelResolverException) {
					// there are plenty of BOMs that include artifacts that don't exist
					Object[] params = { dep };
					logger().debug("Failed to resolve POM for %s", params);
				} else {
					throw e;
				}
			}
		}

		return transformer == null ? bomBuilder.build() : transformer.transform(this, bomBuilder.build());
	}

	private Iterable<Artifact> bomArtifacts() throws BomDecomposerException {
		final Iterator<Dependency> managedDeps = describe(bomArtifact).getManagedDependencies().iterator();
		return new Iterable<Artifact>() {
			@Override
			public Iterator<Artifact> iterator() {
				return new Iterator<Artifact>() {

					@Override
					public boolean hasNext() {
						return managedDeps.hasNext();
					}

					@Override
					public Artifact next() {
						return managedDeps.next().getArtifact();
					}};
			}
		};
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
		return logger == null ? logger = new DefaultMessageWriter().setDebugEnabled(debug) : logger;
	}

	public Model model(Artifact artifact) throws BomDecomposerException {
		return Util.model(resolve(Util.pom(artifact)).getFile());
	}

	public ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
		try {
			return artifactResolver().resolveDescriptor(artifact);
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to resolve artifact descriptor for " + artifact, e);
		}
	}

	public Artifact resolve(Artifact artifact) throws BomDecomposerException {
		try {
			return artifactResolver().resolve(artifact).getArtifact();
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to resolve artifact " + artifact, e);
		}
	}

	public static void main(String[] args) throws Exception {

		final Path pomDir = Paths.get(System.getProperty("user.home")).resolve("git")
				.resolve("quarkus-platform").resolve("bom").resolve("runtime");

		BomDecomposer.config()
				.debug()
				.bomArtifact("io.quarkus", "quarkus-universe-bom", "1.5.2.Final")
				//.bomFile(pomDir.resolve("pom.xml"))
				.checkForUpdates()
				.decompose()
				.visit(DecomposedBomHtmlReportGenerator.builder("target/releases.html")
						.skipOriginsWithSingleRelease()
						.build());
	}
}
