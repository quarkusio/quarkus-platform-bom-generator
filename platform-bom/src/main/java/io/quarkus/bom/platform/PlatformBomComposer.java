package io.quarkus.bom.platform;

import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomTransformer;
import io.quarkus.bom.decomposer.DecomposedBomVisitor;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bom.decomposer.UpdateAvailabilityTransformer;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.util.GlobUtil;
import io.quarkus.registry.util.PlatformArtifacts;
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
import java.util.regex.Pattern;
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
    private final DecomposedBom filteredQuarkusBom;
    private final DecomposedBom generatedQuarkusBom;

    private final MessageWriter logger;
    private final ExtensionCoordsFilterFactory extCoordsFilterFactory;
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

    private VersionConstraintComparator versionComparator;

    public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {
        this(config, MessageWriter.info());
    }

    public PlatformBomComposer(PlatformBomConfig config, MessageWriter logger) throws BomDecomposerException {
        this.config = config;
        this.logger = logger;
        this.resolver = config.artifactResolver();
        this.extCoordsFilterFactory = ExtensionCoordsFilterFactory.newInstance(config, logger);

        this.originalQuarkusBom = BomDecomposer.config()
                .logger(logger)
                .mavenArtifactResolver(resolver())
                .bomArtifact(config.quarkusBom().originalBomArtifact())
                .decompose();

        final ExtensionFilter coreFilter = ExtensionFilter.getInstance(resolver(), logger, config.quarkusBom());
        filteredQuarkusBom = coreFilter.transform(originalQuarkusBom);

        final DecomposedBom.Builder quarkusBomBuilder = DecomposedBom.builder()
                .bomArtifact(config.quarkusBom().generatedBomArtifact())
                .bomSource(PomSource.of(config.quarkusBom().generatedBomArtifact()));
        addPlatformArtifacts(config.quarkusBom(), quarkusBomBuilder);
        filteredQuarkusBom.releases().forEach(r -> {
            final AtomicReference<ProjectRelease.Builder> rbRef = new AtomicReference<>();
            r.dependencies().forEach(d -> {
                final ProjectDependency effectiveDep = effectiveDep(d);
                if (effectiveDep != null) {
                    ProjectRelease.Builder rb = rbRef.get();
                    if (rb == null) {
                        rb = ProjectRelease.builder(r.id());
                        rbRef.set(rb);
                    }
                    rb.add(effectiveDep);
                    quarkusBomDeps.put(effectiveDep.key(), effectiveDep);
                } else {
                    quarkusBomDeps.put(d.key(), d);
                }
            });
            final ProjectRelease.Builder rb = rbRef.get();
            if (rb != null) {
                quarkusBomBuilder.addRelease(rb.build());
            }
        });
        generatedQuarkusBom = quarkusBomBuilder.build();

        final UpdateAvailabilityTransformer updateCheckingTransformer = new UpdateAvailabilityTransformer(resolver, logger);
        for (PlatformBomMemberConfig memberConfig : config.directDeps()) {
            logger.info("Processing " + (memberConfig.originalBomArtifact() == null ? memberConfig.generatedBomArtifact()
                    : memberConfig.originalBomArtifact()));
            memberConfigs.put(memberConfig.key(), memberConfig);
            final Iterable<Dependency> bomDeps;
            transformingBom = memberConfig.isBom() || memberConfig.asDependencyConstraints().size() > 1;
            if (memberConfig.isBom()) {
                bomDeps = managedDepsExcludingQuarkusBom(memberConfig);
            } else {
                bomDeps = memberConfig.asDependencyConstraints();
            }
            DecomposedBom originalBom = BomDecomposer.config()
                    .mavenArtifactResolver(resolver())
                    .dependencies(bomDeps)
                    .logger(logger)
                    .bomArtifact(memberConfig.originalBomArtifact() == null ? memberConfig.generatedBomArtifact()
                            : memberConfig.originalBomArtifact())
                    .decompose();
            originalBom = ExtensionFilter.getInstance(resolver(), logger, memberConfig).transform(originalBom);
            originalBom = updateCheckingTransformer.transform(originalBom);
            if (memberConfig.isBom()) {
                originalImportedBoms.put(originalBom.bomArtifact(), originalBom);
            } else if (memberConfig.asDependencyConstraints().size() > 1) {
                originalImportedBoms.put(memberConfig.generatedBomArtifact(), originalBom);
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

    private void mergeExtensionDeps(ProjectRelease release, Map<AppArtifactKey, ProjectDependency> extensionDeps)
            throws BomDecomposerException {
        for (ProjectDependency dep : release.dependencies()) {
            // the origin may have changed in the release of the dependency
            final ProjectDependency quarkusBomDep = quarkusBomDeps.get(dep.key());
            if (quarkusBomDep != null) {
                quarkusBomDependencyInMemberBom(quarkusBomDep, dep);
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
            if (versionConstraintComparator().compare(currentVersion, newVersion) < 0) {
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
        quarkusVersions = filteredQuarkusBom.releaseVersions(releaseOrigin);
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
                final ProjectDependency quarkusBomDep = quarkusBomDeps.putIfAbsent(dep.key(), dep);
                if (quarkusBomDep != null) {
                    quarkusBomDependencyInMemberBom(quarkusBomDep, dep);
                }
            }
            return;
        }
        final LinkedHashMap<String, ReleaseId> preferredVersions = this.preferredVersions == null
                ? this.preferredVersions = preferredVersions(filteredQuarkusBom.releases(release.id().origin()))
                : this.preferredVersions;
        for (final ProjectDependency memberDep : release.dependencies()) {
            ProjectDependency preferredDep = memberDep;
            if (!preferredVersions.containsKey(memberDep.artifact().getVersion())) {
                for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
                    final Artifact artifact = memberDep.artifact().setVersion(preferredVersion.getKey());
                    if (resolver().resolveOrNull(artifact) != null) {
                        preferredDep = ProjectDependency.create(preferredVersion.getValue(),
                                memberDep.dependency().setArtifact(artifact));
                        break;
                    }
                }
            }
            final ProjectDependency quarkusBomDep = quarkusBomDeps.putIfAbsent(preferredDep.key(), preferredDep);
            if (quarkusBomDep != null) {
                quarkusBomDependencyInMemberBom(quarkusBomDep, memberDep);
            }
        }
    }

    private void quarkusBomDependencyInMemberBom(ProjectDependency quarkusBomDep, ProjectDependency memberDep)
            throws BomDecomposerException {
        if (!quarkusBomDep.artifact().getVersion().equals(memberDep.artifact().getVersion())
                && versionConstraintComparator().hasVersionPreferences()
                && versionConstraintComparator()
                        .isPreferredVersion(new DefaultArtifactVersion(memberDep.artifact().getVersion()))
                && !versionConstraintComparator()
                        .isPreferredVersion(new DefaultArtifactVersion(quarkusBomDep.artifact().getVersion()))) {
            final StringBuilder buf = new StringBuilder();
            buf.append("Preferred constraint ").append(memberDep.artifact()).append(" was rejected in favor of ")
                    .append(quarkusBomDep.artifact()).append(" managed by the quarkus-bom");
            if (config.notPreferredQuarkusBomConstraint() == NotPreferredQuarkusBomConstraint.ERROR) {
                throw new BomDecomposerException(buf.toString());
            }
            if (config.notPreferredQuarkusBomConstraint() == NotPreferredQuarkusBomConstraint.WARN) {
                logger.warn(buf.toString());
            }
        }
    }

    @Override
    public void leaveBom() throws BomDecomposerException {
    }

    private VersionConstraintComparator versionConstraintComparator() {
        if (versionComparator == null) {
            final List<Pattern> preferences;
            if (config.versionConstraintPreferences().isEmpty()) {
                preferences = Collections.emptyList();
            } else {
                preferences = new ArrayList<>(config.versionConstraintPreferences().size());
                for (String expr : config.versionConstraintPreferences()) {
                    preferences.add(Pattern.compile(GlobUtil.toRegexPattern(expr)));
                }
            }
            versionComparator = new VersionConstraintComparator(preferences);
        }
        return versionComparator;
    }

    private LinkedHashMap<String, ReleaseId> preferredVersions(Collection<ProjectRelease> releases) {
        final TreeMap<ArtifactVersion, ReleaseId> treeMap = new TreeMap<>(
                Collections.reverseOrder(versionConstraintComparator()));
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

    private Collection<Dependency> managedDepsExcludingQuarkusBom(PlatformBomMemberConfig member)
            throws BomDecomposerException {
        final Artifact bom = member.originalBomArtifact();
        final ArtifactDescriptorResult bomDescr = describe(bom);
        List<Artifact> importedPlatformBoms = null;
        final List<Dependency> allDeps = bomDescr.getManagedDependencies();
        final Map<AppArtifactKey, Dependency> result = new HashMap<>(allDeps.size());
        final ExtensionCoordsFilter extCoordsFilter = extCoordsFilterFactory.forMember(member);
        for (Dependency dep : allDeps) {
            final Artifact artifact = dep.getArtifact();
            if (PlatformArtifacts.isCatalogArtifactId(artifact.getArtifactId())) {
                if (importedPlatformBoms == null) {
                    importedPlatformBoms = new ArrayList<>(2);
                }
                importedPlatformBoms.add(new DefaultArtifact(artifact.getGroupId(),
                        PlatformArtifacts.ensureBomArtifactId(artifact.getArtifactId()), ArtifactCoords.TYPE_POM,
                        artifact.getVersion()));
            }
            if (extCoordsFilter.isExcludeFromBom(artifact)) {
                continue;
            }
            result.put(key(artifact), dep);
        }

        if (importedPlatformBoms != null) {
            for (Artifact importedPlatformBom : importedPlatformBoms) {
                subtractPlatformBom(result, importedPlatformBom);
            }
        } else {
            bomsNotImportingQuarkusBom.add(bom);
        }
        return result.values();
    }

    private static AppArtifactKey key(Artifact artifact) {
        return new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                artifact.getExtension());
    }

    private void subtractPlatformBom(Map<AppArtifactKey, Dependency> result, Artifact quarkusCoreBom)
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
}
