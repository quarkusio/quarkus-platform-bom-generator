package io.quarkus.bom.platform;

import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomTransformer;
import io.quarkus.bom.decomposer.DecomposedBomVisitor;
import io.quarkus.bom.decomposer.NoopDecomposedBomVisitor;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import io.quarkus.registry.util.GlobUtil;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
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

    private Map<ArtifactKey, ProjectDependency> quarkusBomDeps = new HashMap<>();

    private final ProjectReleaseCollector extReleaseCollector = new ProjectReleaseCollector();

    final Map<ArtifactKey, ProjectDependency> externalExtensionDeps = new HashMap<>();

    private final DecomposedBom platformBom;

    private Map<ArtifactKey, PlatformMember> members = new HashMap<>();

    private PlatformBomConfig config;

    private VersionConstraintComparator versionComparator;

    private PlatformMember memberBeingProcessed;

    private boolean logCommonNotManagedDeps = false;
    private Map<ArtifactKey, Set<String>> commonNotManagedDeps;

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
                .dependencies(getOriginalConstraints(config.quarkusBom(), false))
                .bomArtifact(config.quarkusBom().originalBomCoords() == null ? config.quarkusBom().generatedBomCoords()
                        : config.quarkusBom().originalBomCoords())
                .decompose();

        final ExtensionFilter coreFilter = ExtensionFilter.getInstance(resolver(), logger, config.quarkusBom());
        filteredQuarkusBom = coreFilter.transform(originalQuarkusBom);

        final DecomposedBom.Builder quarkusBomBuilder = DecomposedBom.builder()
                .bomArtifact(config.quarkusBom().generatedBomCoords())
                .bomSource(PomSource.of(config.quarkusBom().generatedBomCoords()));
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
        config.quarkusBom().setOriginalDecomposedBom(originalQuarkusBom);
        config.quarkusBom().setAlignedDecomposedBom(generatedQuarkusBom);

        for (PlatformMember member : config.externalMembers()) {
            logger.info("Processing "
                    + (member.originalBomCoords() == null ? member.generatedBomCoords() : member.originalBomCoords()));
            memberBeingProcessed = member;
            members.put(member.key(), member);
            DecomposedBom originalBom = BomDecomposer.config()
                    .logger(logger)
                    .mavenArtifactResolver(resolver())
                    .dependencies(getOriginalConstraints(member, true))
                    .bomArtifact(member.originalBomCoords() == null ? member.generatedBomCoords() : member.originalBomCoords())
                    .decompose();
            originalBom = ExtensionFilter.getInstance(resolver(), logger, member)
                    .transform(originalBom);
            member.setOriginalDecomposedBom(originalBom);

            transform(originalBom);
        }

        logger.info("Generating " + config.bomArtifact());
        platformBom = generatePlatformBom();

        updateMemberBoms();

        if (logCommonNotManagedDeps) {
            commonNotManagedDeps = new HashMap<>();

            final Set<ArtifactKey> universeConstraints = new HashSet<>();
            for (ProjectRelease r : platformBom.releases()) {
                r.dependencies().forEach(d -> universeConstraints.add(new ArtifactKey(d.artifact().getGroupId(),
                        d.artifact().getArtifactId(), d.artifact().getClassifier(), d.artifact().getExtension())));
            }

            collectNotManagedExtensionDeps(generatedQuarkusBom, config.quarkusBom());
            for (PlatformMember member : config.externalMembers()) {
                collectNotManagedExtensionDeps(member.originalDecomposedBom(), member);
            }
            for (Map.Entry<ArtifactKey, Set<String>> e : commonNotManagedDeps.entrySet()) {
                if (e.getValue().size() == 1 || universeConstraints.contains(e.getKey())) {
                    continue;
                }
                System.out.println(e.getKey());
                for (String s : e.getValue()) {
                    System.out.println("  " + s);
                }
            }
        }
    }

    public DecomposedBom platformBom() {
        return platformBom;
    }

    private void updateMemberBoms() {
        final Set<ArtifactKey> bomDeps = new HashSet<>();
        final Map<ReleaseId, ProjectRelease.Builder> releaseBuilders = new HashMap<>();
        for (PlatformMember member : members.values()) {
            bomDeps.clear();
            releaseBuilders.clear();

            final DecomposedBom importedBomMinusQuarkusBom = member.originalDecomposedBom();
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

            final PlatformMember memberConfig = members
                    .get(new ArtifactKey(importedBomMinusQuarkusBom.bomArtifact().getGroupId(),
                            importedBomMinusQuarkusBom.bomArtifact().getArtifactId()));
            final Artifact generatedBomArtifact = memberConfig.generatedBomCoords();
            final DecomposedBom.Builder updatedBom = DecomposedBom.builder()
                    .bomArtifact(generatedBomArtifact)
                    .bomSource(PomSource.of(generatedBomArtifact));
            for (ProjectRelease.Builder releaseBuilder : releaseBuilders.values()) {
                updatedBom.addRelease(releaseBuilder.build());
            }
            addPlatformArtifacts(member, updatedBom);
            member.setAlignedDecomposedBom(updatedBom.build());
        }
    }

    private void addPlatformArtifacts(PlatformMember memberConfig,
            final DecomposedBom.Builder updatedBom) {
        final Artifact generatedBomArtifact = memberConfig.generatedBomCoords();
        if (!generatedBomArtifact.equals(memberConfig.originalBomCoords())) {
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

    private void collectNotManagedExtensionDeps(DecomposedBom decomposed, PlatformMember config)
            throws BomDecomposerException {
        final List<Dependency> constraints = new ArrayList<>();
        final Set<ArtifactKey> constraintKeys = new HashSet<>();
        for (ProjectRelease r : decomposed.releases()) {
            r.dependencies().forEach(d -> {
                constraints.add(d.dependency());
                constraintKeys.add(new ArtifactKey(d.artifact().getGroupId(), d.artifact().getArtifactId(),
                        d.artifact().getClassifier(), d.artifact().getExtension()));
            });
        }
        final List<String> extensionGroupIds = config.getExtensionGroupIds();
        decomposed.visit(new NoopDecomposedBomVisitor() {
            @Override
            public void visitProjectRelease(ProjectRelease release) {
                for (ProjectDependency dep : release.dependencies()) {
                    Artifact a = dep.artifact();
                    if (!extensionGroupIds.isEmpty() && !extensionGroupIds.contains(a.getGroupId())
                            || !a.getExtension().equals("jar")
                            || a.getArtifactId().endsWith("-deployment")
                            || a.getClassifier().equals("javadoc")
                            || a.getClassifier().equals("sources")
                            || a.getClassifier().equals("tests")
                            || dep.dependency().getScope().equals("test")) {
                        continue;
                    }
                    final ExtensionInfo ext = getExtensionInfoOrNull(a);
                    if (ext == null) {
                        continue;
                    }
                    collectNotManagedDependencies(collectDependencies(a, constraints).getChildren(), constraintKeys);
                    collectNotManagedDependencies(collectDependencies(ext.getDeployment(), constraints).getChildren(),
                            constraintKeys);
                }
            }

            private DependencyNode collectDependencies(Artifact a, final List<Dependency> constraints) {
                final DependencyNode root;
                try {
                    root = resolver()
                            .underlyingResolver().collectManagedDependencies(a, Collections.emptyList(),
                                    constraints, Collections.emptyList(), Collections.emptyList(), "test", "provided")
                            .getRoot();
                } catch (BootstrapMavenException e) {
                    throw new RuntimeException("Failed to collect dependencies of " + a, e);
                }
                return root;
            }
        });
    }

    private void collectNotManagedDependencies(Collection<DependencyNode> depNodes, Set<ArtifactKey> constraints) {
        for (DependencyNode node : depNodes) {
            collectNotManagedDependencies(node.getChildren(), constraints);
            final Artifact a = node.getArtifact();
            final ArtifactKey key = new ArtifactKey(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension());
            if (a == null || constraints.contains(key)) {
                continue;
            }
            commonNotManagedDeps.computeIfAbsent(key, k -> new HashSet<>()).add(a.getVersion());
        }
    }

    private ExtensionInfo getExtensionInfoOrNull(Artifact a) {
        File f = a.getFile();
        if (f == null) {
            f = resolver().resolve(a).getArtifact().getFile();
        }
        final Properties props;
        if (f.isDirectory()) {
            props = loadPropertiesOrNull(f.toPath().resolve(BootstrapConstants.DESCRIPTOR_PATH));
        } else {
            try (FileSystem fs = FileSystems.newFileSystem(f.toPath(), (ClassLoader) null)) {
                props = loadPropertiesOrNull(fs.getPath(BootstrapConstants.DESCRIPTOR_PATH));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read " + f, e);
            }
        }
        if (props == null) {
            return null;
        }
        final String deploymentStr = props.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
        if (deploymentStr == null) {
            throw new IllegalStateException(a + " does not include the corresponding deployment artifact coordinates in its "
                    + BootstrapConstants.DESCRIPTOR_PATH);
        }
        final ArtifactCoords deploymentCoords = ArtifactCoords.fromString(deploymentStr);
        return new ExtensionInfo(a, new DefaultArtifact(deploymentCoords.getGroupId(), deploymentCoords.getArtifactId(),
                deploymentCoords.getClassifier(), deploymentCoords.getType(), deploymentCoords.getVersion()));
    }

    private static Properties loadPropertiesOrNull(Path p) {
        if (!Files.exists(p)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            final Properties props = new Properties();
            props.load(reader);
            return props;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + p, e);
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

        for (Collection<ProjectRelease> releases : extReleaseCollector.getOriginReleaseBuilders()) {

            if (releases.size() == 1) {
                mergeExtensionDeps(releases.iterator().next(), externalExtensionDeps);
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

    private void mergeExtensionDeps(ProjectRelease release, Map<ArtifactKey, ProjectDependency> extensionDeps)
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

    private void addNonQuarkusDep(ProjectDependency dep, Map<ArtifactKey, ProjectDependency> extensionDeps) {
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
            final ProjectRelease.Builder releaseBuilder = extReleaseCollector.getOrCreateReleaseBuilder(release.id(),
                    memberBeingProcessed);
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

    private Collection<Dependency> getOriginalConstraints(PlatformMember member, boolean filtered)
            throws BomDecomposerException {
        Map<ArtifactKey, Dependency> result = null;
        final List<Artifact> importedPlatformBoms = new ArrayList<>(0);
        final ExtensionCoordsFilter extCoordsFilter = extCoordsFilterFactory.forMember(member);
        for (Dependency d : member.inputConstraints()) {
            if ("import".equals(d.getScope())) {
                final Artifact bom = d.getArtifact();
                final ArtifactDescriptorResult bomDescr = describe(bom);
                importedPlatformBoms.clear();
                final List<Dependency> allDeps = bomDescr.getManagedDependencies();
                final Map<ArtifactKey, Dependency> bomConstraints = new HashMap<>(allDeps.size());
                for (Dependency dep : allDeps) {
                    final Artifact artifact = dep.getArtifact();
                    if (filtered && PlatformArtifacts.isCatalogArtifactId(artifact.getArtifactId())) {
                        importedPlatformBoms.add(new DefaultArtifact(artifact.getGroupId(),
                                PlatformArtifacts.ensureBomArtifactId(artifact.getArtifactId()),
                                ArtifactCoords.TYPE_POM, artifact.getVersion()));
                    }
                    if (filtered && extCoordsFilter.isExcludeFromBom(artifact)) {
                        continue;
                    }
                    bomConstraints.put(key(artifact), dep);
                }

                for (Artifact importedPlatformBom : importedPlatformBoms) {
                    subtractPlatformBom(bomConstraints, importedPlatformBom);
                }
                if (result == null) {
                    result = bomConstraints;
                } else {
                    for (Map.Entry<ArtifactKey, Dependency> dep : bomConstraints.entrySet()) {
                        result.putIfAbsent(dep.getKey(), dep.getValue());
                    }
                }
            } else {
                if (result == null) {
                    result = new HashMap<>(member.inputConstraints().size());
                }
                result.put(key(d.getArtifact()), d);
            }
        }
        return result.values();
    }

    private static ArtifactKey key(Artifact artifact) {
        return new ArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                artifact.getExtension());
    }

    private void subtractPlatformBom(Map<ArtifactKey, Dependency> result, Artifact quarkusCoreBom)
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
