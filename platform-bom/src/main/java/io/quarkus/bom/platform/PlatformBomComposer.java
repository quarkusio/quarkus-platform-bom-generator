package io.quarkus.bom.platform;

import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.DecomposedBomTransformer;
import io.quarkus.bom.decomposer.DecomposedBomVisitor;
import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bom.diff.BomDiff;
import io.quarkus.bom.diff.HtmlBomDiffReportGenerator;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.devtools.messagewriter.MessageWriter;
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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class PlatformBomComposer implements DecomposedBomTransformer, DecomposedBomVisitor {

    public static DecomposedBom compose(PlatformBomConfig config) throws BomDecomposerException {
        return new PlatformBomComposer(config).platformBom();
    }

    private final DecomposedBom originalQuarkusBom;
    private final DecomposedBom generatedQuarkusBom;

    private final MessageWriter logger;
    private ArtifactResolver resolver;

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

    private Map<String, PlatformBomMemberConfig> memberConfigs = new HashMap<>();

    private PlatformBomConfig config;

    public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {
        this(config, MessageWriter.info());
    }

    public PlatformBomComposer(PlatformBomConfig config, MessageWriter logger) throws BomDecomposerException {
        this.config = config;
        this.logger = logger;
        this.resolver = config.artifactResolver();

        this.originalQuarkusBom = BomDecomposer.config()
                //.debug()
                .logger(logger)
                .mavenArtifactResolver(resolver())
                .bomArtifact(config.quarkusBom().originalBomArtifact())
                .decompose();
        final DecomposedBom.Builder quarkusBomBuilder = DecomposedBom.builder()
                .bomArtifact(config.quarkusBom().generatedBomArtifact())
                .bomSource(PomSource.of(config.quarkusBom().generatedBomArtifact()));
        addPlatformArtifacts(config.quarkusBom(), quarkusBomBuilder);
        originalQuarkusBom.releases().forEach(r -> {
            final AtomicReference<ProjectRelease.Builder> rbRef = new AtomicReference<>();
            r.dependencies().forEach(d -> {
                quarkusBomDeps.put(d.key(), d);
                final ProjectDependency effectiveDep = effectiveDep(d);
                if (effectiveDep != null) {
                    ProjectRelease.Builder rb = rbRef.get();
                    if (rb == null) {
                        rb = ProjectRelease.builder(r.id());
                        rbRef.set(rb);
                    }
                    rb.add(effectiveDep);
                }
            });
            final ProjectRelease.Builder rb = rbRef.get();
            if (rb != null) {
                quarkusBomBuilder.addRelease(rb.build());
            }
        });
        generatedQuarkusBom = quarkusBomBuilder.build();

        for (PlatformBomMemberConfig memberConfig : config.directDeps()) {
            logger.info("Processing " + memberConfig.originalBomArtifact());
            memberConfigs.put(
                    memberConfig.originalBomArtifact().getGroupId() + ":" + memberConfig.originalBomArtifact().getArtifactId(),
                    memberConfig);
            final Iterable<Dependency> bomDeps;
            transformingBom = memberConfig.isBom();
            if (transformingBom) {
                bomDeps = managedDepsExcludingQuarkusBom(memberConfig.originalBomArtifact());
            } else {
                bomDeps = Collections.singleton(memberConfig.asDependencyConstraint());
            }

            final DecomposedBom originalBom = BomDecomposer.config()
                    .mavenArtifactResolver(resolver())
                    .dependencies(bomDeps)
                    // .debug()
                    .logger(logger)
                    .bomArtifact(memberConfig.originalBomArtifact())
                    .checkForUpdates()
                    .decompose();
            if (memberConfig.isBom()) {
                originalImportedBoms.put(originalBom.bomArtifact(), originalBom);
            }
            transform(originalBom);
        }

        logger.info("Generating " + config.bomArtifact());
        platformBom = generatePlatformBom();

        generatedUpdatedImportedBoms();
    }

    public DecomposedBom originalQuarkusCoreBom() {
        return originalQuarkusBom;
    }

    public DecomposedBom generatedQuarkusCoreBom() {
        return generatedQuarkusBom;
    }

    public DecomposedBom platformBom() {
        return platformBom;
    }

    public List<DecomposedBom> alignedMemberBoms() {
        return importedBoms;
    }

    public DecomposedBom originalMemberBom(Artifact artifact) {
        return originalImportedBoms.get(artifact);
    }

    private void generatedUpdatedImportedBoms() {
        final Set<AppArtifactKey> bomDeps = new HashSet<>();
        final Map<ReleaseId, ProjectRelease.Builder> releaseBuilders = new HashMap<>();
        int i = 0;
        while (i < importedBoms.size()) {
            bomDeps.clear();
            releaseBuilders.clear();

            final DecomposedBom importedBomMinusQuarkusBom = importedBoms.get(i);
            /* @formatter:off exclude quarkus-bom from the platform member boms
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
            @formatter:on */

            for (ProjectRelease release : importedBomMinusQuarkusBom.releases()) {
                for (ProjectDependency dep : release.dependencies()) {
                    if (!bomDeps.add(dep.key()) || config.excluded(dep.key())) {
                        continue;
                    }
                    ProjectDependency platformDep = quarkusBomDeps.get(dep.key());
                    if (platformDep == null) {
                        platformDep = externalExtensionDeps.get(dep.key());
                    }
                    if (platformDep == null) {
                        throw new IllegalStateException("Failed to locate " + dep.key() + " in the generated platform BOM");
                    }
                    releaseBuilders.computeIfAbsent(platformDep.releaseId(), id -> ProjectRelease.builder(id)).add(platformDep);
                }
            }

            final PlatformBomMemberConfig memberConfig = memberConfigs.get(importedBomMinusQuarkusBom.bomArtifact().getGroupId()
                    + ":" + importedBomMinusQuarkusBom.bomArtifact().getArtifactId());
            final Artifact generatedBomArtifact = memberConfig.generatedBomArtifact();
            final DecomposedBom.Builder updatedBom = DecomposedBom.builder()
                    .bomArtifact(generatedBomArtifact)
                    .bomSource(PomSource.of(generatedBomArtifact));
            for (ProjectRelease.Builder releaseBuilder : releaseBuilders.values()) {
                updatedBom.addRelease(releaseBuilder.build());
            }
            addPlatformArtifacts(memberConfig, updatedBom);
            importedBoms.set(i++, updatedBom.build());
        }
    }

    private void addPlatformArtifacts(PlatformBomMemberConfig memberConfig,
            final DecomposedBom.Builder updatedBom) {
        final Artifact generatedBomArtifact = memberConfig.generatedBomArtifact();
        if (!generatedBomArtifact.equals(memberConfig.originalBomArtifact())) {
            // member platform descriptor artifact
            final ReleaseId memberReleaseId = ReleaseIdFactory.create(
                    ReleaseOrigin.Factory.ga(generatedBomArtifact.getGroupId(), generatedBomArtifact.getArtifactId()),
                    ReleaseVersion.Factory.version(generatedBomArtifact.getVersion()));
            ProjectRelease memberRelease = ProjectRelease.builder(memberReleaseId)
                    .add(ProjectDependency.create(memberReleaseId,
                            new DefaultArtifact(generatedBomArtifact.getGroupId(),
                                    generatedBomArtifact.getArtifactId()
                                            + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                                    generatedBomArtifact.getVersion(), "json", generatedBomArtifact.getVersion())))
                    .add(ProjectDependency.create(memberReleaseId,
                            new DefaultArtifact(generatedBomArtifact.getGroupId(),
                                    generatedBomArtifact.getArtifactId()
                                            + BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX,
                                    null, "properties", generatedBomArtifact.getVersion())))
                    .build();
            updatedBom.addRelease(memberRelease);
        }
    }

    private DecomposedBom generatePlatformBom() throws BomDecomposerException {
        final Map<ReleaseId, ProjectRelease.Builder> platformReleaseBuilders = new HashMap<>();

        // platform descriptor artifact
        final ReleaseId bomReleaseId = ReleaseIdFactory.create(
                ReleaseOrigin.Factory.ga(config.bomArtifact().getGroupId(), config.bomArtifact().getArtifactId()),
                ReleaseVersion.Factory.version(config.bomArtifact().getVersion()));
        final ProjectRelease.Builder bomReleaseBuilder = ProjectRelease.builder(bomReleaseId)
                .add(ProjectDependency.create(bomReleaseId,
                        new DefaultArtifact(config.bomArtifact().getGroupId(),
                                config.bomArtifact().getArtifactId()
                                        + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                                config.bomArtifact().getVersion(), "json", config.bomArtifact().getVersion())));
        if (config.includePlatformProperties()) {
            bomReleaseBuilder.add(ProjectDependency.create(bomReleaseId,
                    new DefaultArtifact(config.bomArtifact().getGroupId(),
                            config.bomArtifact().getArtifactId()
                                    + BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX,
                            null, "properties", config.bomArtifact().getVersion())));
        }
        platformReleaseBuilders.put(bomReleaseId, bomReleaseBuilder);

        for (ProjectDependency dep : quarkusBomDeps.values()) {
            dep = effectiveDep(dep);
            if (dep == null) {
                continue;
            }
            platformReleaseBuilders.computeIfAbsent(dep.releaseId(), id -> ProjectRelease.builder(id)).add(dep);
        }

        for (Map<ReleaseVersion, ProjectRelease.Builder> extReleaseBuilders : externalReleaseDeps.values()) {
            final List<ProjectRelease> releases = new ArrayList<>(extReleaseBuilders.size());
            extReleaseBuilders.values().forEach(b -> releases.add(b.build()));

            if (extReleaseBuilders.size() == 1) {
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
                                if (resolver().resolveOrNull(artifact) != null) {
                                    // logger.info(" EXISTS IN " + preferredVersion);
                                    dep = ProjectDependency.create(preferredVersion.getValue(),
                                            dep.dependency().setArtifact(artifact));
                                    break;
                                }
                            }
                        }
                        addNonQuarkusDep(dep, externalExtensionDeps);
                    }
                }
            }
        }

        for (ProjectDependency dep : externalExtensionDeps.values()) {
            platformReleaseBuilders.computeIfAbsent(dep.releaseId(), id -> ProjectRelease.builder(id)).add(dep);
        }
        final DecomposedBom.Builder platformBuilder = DecomposedBom.builder().bomArtifact(config.bomArtifact())
                .bomSource(config.bomResolver());
        for (ProjectRelease.Builder builder : platformReleaseBuilders.values()) {
            platformBuilder.addRelease(builder.build());
        }
        return platformBuilder.build();
    }

    private ProjectDependency effectiveDep(ProjectDependency dep) {
        if (config.excluded(dep.key())) {
            return null;
        }
        Artifact enforced = config.enforced(dep.key());
        if (enforced == null) {
            return dep;
        }
        return ProjectDependency.create(dep.releaseId(), dep.dependency().setArtifact(enforced));
    }

    private void mergeExtensionDeps(ProjectRelease release, Map<AppArtifactKey, ProjectDependency> extensionDeps) {
        for (ProjectDependency dep : release.dependencies()) {
            // the origin may have changed in the release of the dependency
            if (quarkusBomDeps.containsKey(dep.key())) {
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
        if (enforced != null) {
            if (!extensionDeps.containsKey(dep.key())) {
                extensionDeps.put(dep.key(), ProjectDependency.create(dep.releaseId(), enforced));
            }
            return;
        }
        final ProjectDependency currentDep = extensionDeps.get(dep.key());
        if (currentDep != null) {
            final ArtifactVersion currentVersion = new DefaultArtifactVersion(currentDep.artifact().getVersion());
            final ArtifactVersion newVersion = new DefaultArtifactVersion(dep.artifact().getVersion());
            if (currentVersion.compareTo(newVersion) < 0) {
                extensionDeps.put(dep.key(), dep);
            }
        } else {
            extensionDeps.put(dep.key(), dep);
        }
    }

    @Override
    public DecomposedBom transform(DecomposedBom decomposedBom) throws BomDecomposerException {
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
        quarkusVersions = originalQuarkusBom.releaseVersions(releaseOrigin);
        return true;
    }

    @Override
    public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
    }

    @Override
    public void visitProjectRelease(ProjectRelease release) throws BomDecomposerException {
        if (quarkusVersions.isEmpty()) {
            final ProjectRelease.Builder releaseBuilder = externalReleaseDeps
                    .computeIfAbsent(release.id().origin(), id -> new HashMap<>())
                    .computeIfAbsent(release.id().version(), id -> ProjectRelease.builder(release.id()));
            for (ProjectDependency dep : release.dependencies()) {
                releaseBuilder.add(dep);
            }
            return;
        }
        if (quarkusVersions.contains(release.id().version())) {
            for (ProjectDependency dep : release.dependencies()) {
                quarkusBomDeps.putIfAbsent(dep.key(), dep);
            }
            return;
        }
        //logger.error("CONFLICT: " + extBom + " includes " + release.id() + " while Quarkus includes " + quarkusVersions);
        final LinkedHashMap<String, ReleaseId> preferredVersions = this.preferredVersions == null
                ? this.preferredVersions = preferredVersions(originalQuarkusBom.releases(release.id().origin()))
                : this.preferredVersions;
        for (ProjectDependency dep : release.dependencies()) {
            final String depVersion = dep.artifact().getVersion();
            if (!preferredVersions.containsKey(depVersion)) {
                for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
                    final Artifact artifact = dep.artifact().setVersion(preferredVersion.getKey());
                    if (resolver().resolveOrNull(artifact) != null) {
                        //logger.info("  EXISTS IN " + preferredVersion);
                        dep = ProjectDependency.create(preferredVersion.getValue(), dep.dependency().setArtifact(artifact));
                        break;
                    }
                }
            }
            quarkusBomDeps.putIfAbsent(dep.key(), dep);
        }
    }

    @Override
    public void leaveBom() throws BomDecomposerException {
    }

    private LinkedHashMap<String, ReleaseId> preferredVersions(Collection<ProjectRelease> releases) {
        final TreeMap<ArtifactVersion, ReleaseId> treeMap = new TreeMap<>(Collections.reverseOrder());
        for (ProjectRelease release : releases) {
            for (String versionStr : release.artifactVersions()) {
                final DefaultArtifactVersion version = new DefaultArtifactVersion(versionStr);
                final ReleaseId prevReleaseId = treeMap.put(version, release.id());
                if (prevReleaseId != null && new DefaultArtifactVersion(prevReleaseId.version().asString())
                        .compareTo(new DefaultArtifactVersion(release.id().version().asString())) > 0) {
                    treeMap.put(version, prevReleaseId);
                }
            }
        }
        final LinkedHashMap<String, ReleaseId> result = new LinkedHashMap<>(treeMap.size());
        for (Map.Entry<ArtifactVersion, ReleaseId> entry : treeMap.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    private Collection<Dependency> managedDepsExcludingQuarkusBom(Artifact bom) throws BomDecomposerException {
        final ArtifactDescriptorResult bomDescr = describe(bom);
        Artifact quarkusCore = null;
        final List<Dependency> allDeps = bomDescr.getManagedDependencies();
        final Map<AppArtifactKey, Dependency> result = new HashMap<>(allDeps.size());
        for (Dependency dep : allDeps) {
            final Artifact artifact = dep.getArtifact();
            result.put(key(artifact), dep);
            if (quarkusCore == null && artifact.getArtifactId().equals("quarkus-core")
                    && artifact.getGroupId().equals("io.quarkus")) {
                quarkusCore = artifact;
            }
        }

        if (quarkusCore != null) {
            subtractQuarkusBom(result,
                    new DefaultArtifact("io.quarkus", "quarkus-bom", null, "pom", quarkusCore.getVersion()));
        } else {
            bomsNotImportingQuarkusBom.add(bom);
        }
        return result.values();
    }

    private static AppArtifactKey key(Artifact artifact) {
        return new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                artifact.getExtension());
    }

    private void subtractQuarkusBom(Map<AppArtifactKey, Dependency> result, Artifact quarkusCoreBom)
            throws BomDecomposerException {
        try {
            final ArtifactDescriptorResult quarkusBomDescr = describe(quarkusCoreBom);
            for (Dependency quarkusBomDep : quarkusBomDescr.getManagedDependencies()) {
                result.remove(key(quarkusBomDep.getArtifact()));
            }
        } catch (BomDecomposerException e) {
            logger.debug("Failed to subtract %s: %s", quarkusCoreBom, e.getLocalizedMessage());
            throw e;
        }
    }

    private ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
        return resolver().describe(artifact);
    }

    private ArtifactResolver resolver() {
        return resolver == null ? resolver = ArtifactResolverProvider.get() : resolver;
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

            final Path generatedReleasesFile = generateReleasesHtml(generatedBom, outputDir);
            index.mainBom(generatedBom.bomResolver().pomPath().toUri().toURL(), generatedBom, generatedReleasesFile);

            for (DecomposedBom importedBom : bomComposer.alignedMemberBoms()) {
                report(bomComposer.originalMemberBom(importedBom.bomArtifact()), importedBom, outputDir, index);
            }
        }
    }

    private static void report(DecomposedBom originalBom, DecomposedBom generatedBom, Path outputDir,
            ReportIndexPageGenerator index)
            throws IOException, BomDecomposerException {
        outputDir = outputDir.resolve(bomDirName(generatedBom.bomArtifact()));
        final Path platformBomXml = outputDir.resolve("pom.xml");
        PomUtils.toPom(generatedBom, platformBomXml);

        final BomDiff.Config config = BomDiff.config();
        if (generatedBom.bomResolver().isResolved()) {
            config.compare(generatedBom.bomResolver().pomPath());
        } else {
            config.compare(generatedBom.bomArtifact());
        }
        final BomDiff bomDiff = config.to(platformBomXml);

        final Path diffFile = outputDir.resolve("diff.html");
        HtmlBomDiffReportGenerator.config(diffFile).report(bomDiff);

        final Path generatedReleasesFile = generateReleasesHtml(generatedBom, outputDir);

        final Path originalReleasesFile = outputDir.resolve("original-releases.html");
        originalBom.visit(DecomposedBomHtmlReportGenerator.builder(originalReleasesFile)
                .skipOriginsWithSingleRelease().build());

        index.bomReport(bomDiff.mainUrl(), bomDiff.toUrl(), generatedBom, originalReleasesFile, generatedReleasesFile,
                diffFile);
    }

    private static Path generateReleasesHtml(DecomposedBom generatedBom, Path outputDir) throws BomDecomposerException {
        final Path generatedReleasesFile = outputDir.resolve("generated-releases.html");
        generatedBom.visit(DecomposedBomHtmlReportGenerator.builder(generatedReleasesFile)
                .skipOriginsWithSingleRelease().build());
        return generatedReleasesFile;
    }

    private static String bomDirName(Artifact a) {
        return a.getGroupId() + "." + a.getArtifactId() + "-" + a.getVersion();
    }
}
