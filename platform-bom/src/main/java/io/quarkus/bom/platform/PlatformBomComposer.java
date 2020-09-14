package io.quarkus.bom.platform;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.DecomposedBomTransformer;
import io.quarkus.bom.decomposer.DecomposedBomVisitor;
import io.quarkus.bom.decomposer.DefaultMessageWriter;
import io.quarkus.bom.decomposer.MessageWriter;
import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bom.diff.BomDiff;
import io.quarkus.bom.diff.HtmlBomDiffReportGenerator;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

public class PlatformBomComposer implements DecomposedBomTransformer, DecomposedBomVisitor {

	public static DecomposedBom compose(PlatformBomConfig config) throws BomDecomposerException {
		return new PlatformBomComposer(config).platformBom();
	}

	private final DecomposedBom quarkusBom;
	private final MessageWriter logger = new DefaultMessageWriter();
	private MavenArtifactResolver resolver;

	private Collection<ReleaseVersion> quarkusVersions;
	private LinkedHashMap<String, ReleaseId> preferredVersions;
	private boolean transformingBom;

	private Map<AppArtifactKey, ProjectDependency> quarkusBomDeps = new HashMap<>();

	private Map<ReleaseOrigin, Map<ReleaseVersion, ProjectRelease.Builder>> externalReleaseDeps = new HashMap<>();

	final Map<AppArtifactKey, ProjectDependency> externalExtensionDeps = new HashMap<>();

	private List<DecomposedBom> importedBoms = new ArrayList<>();
	private Map<Artifact, DecomposedBom> originalImportedBoms = new HashMap<>();
	private Set<Artifact> bomsNotImportingQuarkusBom = new HashSet<>();

	private final DecomposedBom platformBom;
	private final DecomposedBom originalPlatformBom;

	private PlatformBomConfig config;

	public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {

		this.config = config;

		originalPlatformBom = BomDecomposer.config()
				.mavenArtifactResolver(resolver())
				// .debug()
				.logger(logger)
				.bomArtifact(config.bomArtifact())
				.checkForUpdates()
				.decompose();

		this.quarkusBom = BomDecomposer.config()
				//.debug()
				.logger(logger)
				.mavenArtifactResolver(resolver())
				.bomArtifact(config.quarkusBom())
				.decompose();
		quarkusBom.releases().forEach(r -> {
			r.dependencies().forEach(d -> quarkusBomDeps.put(d.key(), d));
		});

		for(Artifact directDep : config.directDeps()) {
			final Iterable<Artifact> artifacts;
			transformingBom = directDep.getExtension().equals("pom");
			if(transformingBom) {
				artifacts = managedDepsExcludingQuarkusBom(directDep);
				final DecomposedBom originalBom = BomDecomposer.config()
						.mavenArtifactResolver(resolver())
						// .debug()
						.logger(logger)
						.bomArtifact(directDep)
						.checkForUpdates()
						.decompose();
				originalImportedBoms.put(originalBom.bomArtifact(), originalBom);
			} else {
				artifacts = Collections.singleton(directDep);
			}
			BomDecomposer.config()
					.mavenArtifactResolver(resolver())
					//.debug()
					.logger(logger)
					.bomArtifact(directDep)
					.checkForUpdates()
					.artifacts(artifacts)
					.transform(this)
					.decompose();
		}

		platformBom = generatePlatformBom();

		generatedUpdatedImportedBoms();
	}

	public DecomposedBom originalPlatformBom() {
		return originalPlatformBom;
	}

	public DecomposedBom platformBom() {
		return platformBom;
	}

	public List<DecomposedBom> upgradedImportedBoms() {
		return importedBoms;
	}

	public DecomposedBom originalImportedBom(Artifact artifact) {
		return originalImportedBoms.get(artifact);
	}

	private void generatedUpdatedImportedBoms() {
		final Set<AppArtifactKey> bomDeps = new HashSet<>();
		final Map<ReleaseId, ProjectRelease.Builder> releaseBuilders = new HashMap<>();
		int i = 0;
		while(i < importedBoms.size()) {
			bomDeps.clear();
			releaseBuilders.clear();

			final DecomposedBom importedBomMinusQuarkusBom = importedBoms.get(i);
			if (!bomsNotImportingQuarkusBom.contains(importedBomMinusQuarkusBom.bomArtifact())) {
				quarkusBom.releases().forEach(r -> {
					r.dependencies().forEach(d -> {
						if (!config.excluded(d.key())) {
							bomDeps.add(d.key());
							releaseBuilders.computeIfAbsent(d.releaseId(), id -> ProjectRelease.builder(id)).add(d);
						}
					});
				});
			}

			for(ProjectRelease release : importedBomMinusQuarkusBom.releases()) {
				for(ProjectDependency dep : release.dependencies()) {
					if(!bomDeps.add(dep.key()) || config.excluded(dep.key())) {
						continue;
					}
					ProjectDependency platformDep = quarkusBomDeps.get(dep.key());
					if(platformDep == null) {
						platformDep = externalExtensionDeps.get(dep.key());
					}
					if(platformDep == null) {
						throw new IllegalStateException("Failed to locate " + dep.key() + " in the generated platform BOM");
					}
					releaseBuilders.computeIfAbsent(platformDep.releaseId(), id -> ProjectRelease.builder(id)).add(platformDep);
				}
			}

			final DecomposedBom.Builder updatedBom = DecomposedBom.builder()
					.bomArtifact(importedBomMinusQuarkusBom.bomArtifact())
					.bomSource(PomSource.of(importedBomMinusQuarkusBom.bomArtifact()));
			for(ProjectRelease.Builder releaseBuilder : releaseBuilders.values()) {
				updatedBom.addRelease(releaseBuilder.build());
			}
			importedBoms.set(i++, updatedBom.build());
		}
	}

	private DecomposedBom generatePlatformBom() throws BomDecomposerException {
		final Map<ReleaseId, ProjectRelease.Builder> platformReleaseBuilders = new HashMap<>();
		for(ProjectDependency dep : quarkusBomDeps.values()) {
			dep = effectiveDep(dep);
			if(dep == null) {
				continue;
			}
			platformReleaseBuilders.computeIfAbsent(dep.releaseId(), id -> ProjectRelease.builder(id)).add(dep);
		}

		for(Map<ReleaseVersion, ProjectRelease.Builder> extReleaseBuilders : externalReleaseDeps.values()) {
			final List<ProjectRelease> releases = new ArrayList<>(extReleaseBuilders.size());
			extReleaseBuilders.values().forEach(b -> releases.add(b.build()));

			if(extReleaseBuilders.size() == 1) {
    		    mergeExtensionDeps(releases.get(0), externalExtensionDeps);
				continue;
			}

			final LinkedHashMap<String, ReleaseId> preferredVersions = preferredVersions(releases);
			for (ProjectRelease release : releases) {
				for (Map.Entry<String, ReleaseId> preferred : preferredVersions.entrySet()) {
					if (release.id().equals(preferred.getValue())) {
						mergeExtensionDeps(release, externalExtensionDeps);
						break;
					}
					for (ProjectDependency dep : release.dependencies()) {
						if (quarkusBomDeps.containsKey(dep.key())) {
							continue;
						}
						final String depVersion = dep.artifact().getVersion();
						if (!preferred.getKey().equals(depVersion)) {
							for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
								final Artifact artifact = dep.artifact().setVersion(preferredVersion.getKey());
								try {
									resolver().resolve(artifact);
									// logger.info(" EXISTS IN " + preferredVersion);
									dep = ProjectDependency.create(preferredVersion.getValue(), artifact);
									break;
								} catch (BootstrapMavenException e) {
								}
							}
						}
						addNonQuarkusDep(dep, externalExtensionDeps);
					}
				}
			}
		}

		for(ProjectDependency dep : externalExtensionDeps.values()) {
			platformReleaseBuilders.computeIfAbsent(dep.releaseId(), id -> ProjectRelease.builder(id)).add(dep);
		}
		final DecomposedBom.Builder platformBuilder = DecomposedBom.builder().bomArtifact(config.bomArtifact()).bomSource(config.bomResolver());
		for (ProjectRelease.Builder builder : platformReleaseBuilders.values()) {
			platformBuilder.addRelease(builder.build());
		}
		return platformBuilder.build();
	}

	private ProjectDependency effectiveDep(ProjectDependency dep) {
		if(config.excluded(dep.key())) {
			return null;
		}
		Artifact enforced = config.enforced(dep.key());
		if(enforced == null) {
			return dep;
		}
		return ProjectDependency.create(dep.releaseId(), enforced);
	}

	private void mergeExtensionDeps(ProjectRelease release, Map<AppArtifactKey, ProjectDependency> extensionDeps) {
		for(ProjectDependency dep : release.dependencies()) {
			// the origin may have changed in the release of the dependency
			if(quarkusBomDeps.containsKey(dep.key())) {
				return;
			}
			addNonQuarkusDep(dep, extensionDeps);
		}
	}

	private void addNonQuarkusDep(ProjectDependency dep, Map<AppArtifactKey, ProjectDependency> extensionDeps) {
		if (config.excluded(dep.key())) {
			return;
		}
		final Artifact enforced = config.enforced(dep.key());
		if(enforced != null) {
			if(!extensionDeps.containsKey(dep.key())) {
			    extensionDeps.put(dep.key(), ProjectDependency.create(dep.releaseId(), enforced));
			}
			return;
		}
		final ProjectDependency currentDep = extensionDeps.get(dep.key());
		if(currentDep != null) {
			final ArtifactVersion currentVersion = new DefaultArtifactVersion(currentDep.artifact().getVersion());
			final ArtifactVersion newVersion = new DefaultArtifactVersion(dep.artifact().getVersion());
			if(currentVersion.compareTo(newVersion) < 0) {
				extensionDeps.put(dep.key(), dep);
			}
		} else {
		    extensionDeps.put(dep.key(), dep);
		}
	}

	@Override
	public DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom)
			throws BomDecomposerException {
		if (transformingBom) {
			this.importedBoms.add(decomposedBom);
		}
		decomposedBom.visit(this);
		return decomposedBom;
	}

	private Artifact extBom;

	@Override
	public void enterBom(Artifact bomArtifact) {
		extBom = bomArtifact;
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		preferredVersions = null;
		quarkusVersions = quarkusBom.releaseVersions(releaseOrigin);
		return true;
	}

	@Override
	public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) throws BomDecomposerException {
		if(quarkusVersions.isEmpty()) {
			final ProjectRelease.Builder releaseBuilder = externalReleaseDeps
					.computeIfAbsent(release.id().origin(), id -> new HashMap<>())
					.computeIfAbsent(release.id().version(), id -> ProjectRelease.builder(release.id()));
			for(ProjectDependency dep : release.dependencies()) {
				releaseBuilder.add(dep);
			}
			return;
		}
		if(quarkusVersions.contains(release.id().version())) {
			for(ProjectDependency dep : release.dependencies()) {
				quarkusBomDeps.putIfAbsent(dep.key(), dep);
			}
			return;
		}
		//logger.error("CONFLICT: " + extBom + " includes " + release.id() + " while Quarkus includes " + quarkusVersions);
		final LinkedHashMap<String, ReleaseId> preferredVersions = this.preferredVersions == null ? this.preferredVersions = preferredVersions(quarkusBom.releases(release.id().origin())) : this.preferredVersions;
	    for(ProjectDependency dep : release.dependencies()) {
	    	final String depVersion = dep.artifact().getVersion();
			if (!preferredVersions.containsKey(depVersion)) {
				for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
					final Artifact artifact = dep.artifact().setVersion(preferredVersion.getKey());
					try {
						resolver().resolve(artifact);
						//logger.info("  EXISTS IN " + preferredVersion);
						dep = ProjectDependency.create(preferredVersion.getValue(), artifact);
						break;
					} catch (BootstrapMavenException e) {
					}
				}
			}
			quarkusBomDeps.putIfAbsent(dep.key(), dep);
	    }
	}

	@Override
	public void leaveBom() throws BomDecomposerException {
		// TODO Auto-generated method stub

	}

	private LinkedHashMap<String,ReleaseId> preferredVersions(Collection<ProjectRelease> releases) {
		final TreeMap<ArtifactVersion, ReleaseId> treeMap = new TreeMap<>(Collections.reverseOrder());
		for (ProjectRelease release : releases) {
			for(String versionStr : release.artifactVersions()) {
			    final DefaultArtifactVersion version = new DefaultArtifactVersion(versionStr);
				final ReleaseId prevReleaseId = treeMap.put(version, release.id());
				if (prevReleaseId != null && new DefaultArtifactVersion(prevReleaseId.version().asString())
						.compareTo(new DefaultArtifactVersion(release.id().version().asString())) > 0) {
					treeMap.put(version, prevReleaseId);
				}
			}
		}
		final LinkedHashMap<String, ReleaseId> result = new LinkedHashMap<>(treeMap.size());
		for(Map.Entry<ArtifactVersion, ReleaseId> entry : treeMap.entrySet()) {
			result.put(entry.getKey().toString(), entry.getValue());
		}
		return result;
	}

	private Set<Artifact> managedDepsExcludingQuarkusBom(Artifact bom) throws BomDecomposerException {
		final Set<Artifact> result = new HashSet<>();
		final ArtifactDescriptorResult bomDescr = describe(bom);
		Artifact quarkusCore = null;
		Artifact quarkusCoreDeployment = null;
		for(Dependency dep : bomDescr.getManagedDependencies()) {
			final Artifact artifact = dep.getArtifact();
			result.add(artifact);
			if(quarkusCoreDeployment == null) {
				if(artifact.getArtifactId().equals("quarkus-core-deployment") && artifact.getGroupId().equals("io.quarkus")) {
					quarkusCoreDeployment = artifact;
				} else if(quarkusCore == null && artifact.getArtifactId().equals("quarkus-core") && artifact.getGroupId().equals("io.quarkus")) {
					quarkusCore = artifact;
				}
			}
		}
		if(quarkusCoreDeployment != null) {
			substructQuarkusBom(result, new DefaultArtifact("io.quarkus", "quarkus-bom-deployment", null, "pom", quarkusCore.getVersion()));
		} else if(quarkusCore != null) {
			substructQuarkusBom(result, new DefaultArtifact("io.quarkus", "quarkus-bom", null, "pom", quarkusCore.getVersion()));
		} else {
			bomsNotImportingQuarkusBom.add(bom);
		}
		return result;
	}

	private void substructQuarkusBom(final Set<Artifact> result, Artifact quarkusCoreBom) throws BomDecomposerException {
		try {
			final ArtifactDescriptorResult quarkusBomDescr = describe(quarkusCoreBom);
			for (Dependency quarkusBomDep : quarkusBomDescr.getManagedDependencies()) {
				result.remove(quarkusBomDep.getArtifact());
			}
		} catch (BomDecomposerException e) {
			logger.debug("Failed to describe %s", quarkusCoreBom);
		}
	}

	private ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
		try {
			return resolver().resolveDescriptor(artifact);
		} catch (BootstrapMavenException e) {
			throw new BomDecomposerException("Failed to describe " + artifact, e);
		}
	}

	private MavenArtifactResolver resolver() {
		try {
			return resolver == null ? resolver = MavenArtifactResolver.builder().build() : resolver;
		} catch (BootstrapMavenException e) {
			throw new IllegalStateException("Failed to initialize Maven artifact resolver", e);
		}
	}

	public static void main(String[] args) throws Exception {
		final Path srcPomDir = Paths.get(System.getProperty("user.home")).resolve("git")
				.resolve("quarkus-platform").resolve("bom");

		final Path outputDir = Paths.get("target").resolve("boms"); // pomDir

		PlatformBomConfig config = PlatformBomConfig.builder()
				.pomResolver(PomSource.of(srcPomDir.resolve("pom.xml")))
				.build();
		//PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.githubPom("quarkusio/quarkus-platform/1.5.2.Final/bom/runtime/pom.xml"));
		//PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.githubPom("quarkusio/quarkus-platform/master/bom/pom.xml"));

		try (ReportIndexPageGenerator index = new ReportIndexPageGenerator(outputDir.resolve("index.html"))) {
			final PlatformBomComposer bomComposer = new PlatformBomComposer(config);
			final DecomposedBom generatedBom = bomComposer.platformBom();

			report(bomComposer.originalPlatformBom(), generatedBom, outputDir, index);

			for (DecomposedBom importedBom : bomComposer.upgradedImportedBoms()) {
				report(bomComposer.originalImportedBom(importedBom.bomArtifact()), importedBom, outputDir, index);
			}
		}
	}

	private static void report(DecomposedBom originalBom, DecomposedBom generatedBom, Path outputDir, ReportIndexPageGenerator index)
			throws IOException, BomDecomposerException {
		outputDir = outputDir.resolve(bomDirName(generatedBom.bomArtifact()));
		final Path platformBomXml = outputDir.resolve("pom.xml");
		PomUtils.toPom(generatedBom, platformBomXml);

		final BomDiff.Config config = BomDiff.config();
		if(generatedBom.bomResolver().isResolved()) {
			config.compare(generatedBom.bomResolver().pomPath());
		} else {
			config.compare(generatedBom.bomArtifact());
		}
		final BomDiff bomDiff = config.to(platformBomXml);

		final Path diffFile = outputDir.resolve("diff.html");
		HtmlBomDiffReportGenerator.config(diffFile).report(bomDiff);

		final Path generatedReleasesFile = outputDir.resolve("generated-releases.html");
		generatedBom.visit(DecomposedBomHtmlReportGenerator.builder(generatedReleasesFile)
				.skipOriginsWithSingleRelease().build());


		final Path originalReleasesFile = outputDir.resolve("original-releases.html");
		originalBom.visit(DecomposedBomHtmlReportGenerator.builder(originalReleasesFile)
				.skipOriginsWithSingleRelease().build());

		index.bomReport(bomDiff.mainUrl(), bomDiff.toUrl(), generatedBom, originalReleasesFile, generatedReleasesFile, diffFile);
	}

	private static String bomDirName(Artifact a) {
		return a.getGroupId() + "." + a.getArtifactId() + "-" + a.getVersion();
	}
}
