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
import io.quarkus.bom.platform.version.PlatformVersionIncrementor;
import io.quarkus.bom.platform.version.PncVersionIncrementor;
import io.quarkus.bom.platform.version.SpPlatformVersionIncrementor;
import io.quarkus.bom.resolver.ArtifactNotFoundException;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.registry.Constants;
import io.quarkus.registry.util.PlatformArtifacts;
import io.quarkus.util.GlobUtil;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class PlatformBomComposer implements DecomposedBomTransformer, DecomposedBomVisitor {

    private static final String LOG_COMMON_NOT_MANAGED_DEPS = "logCommonNotManagedDeps";

    public static DecomposedBom compose(PlatformBomConfig config) throws BomDecomposerException {
        return new PlatformBomComposer(config).platformBom();
    }

    private final Map<ReleaseId, ProjectRelease.Builder> quarkusBomReleaseBuilders = new HashMap<>();

    private final MessageWriter logger;
    private final ExtensionCoordsFilterFactory extCoordsFilterFactory;
    private ArtifactResolver resolver;

    private Collection<ReleaseVersion> preferredVersions = new HashSet<>();
    private LinkedHashMap<String, ReleaseId> preferredReleaseByVersion;
    private Map<ReleaseOrigin, Collection<ProjectRelease>> preferredReleases = new HashMap<>();

    private Map<ArtifactKey, ProjectDependency> preferredDeps = new HashMap<>();

    private final ProjectReleaseCollector extReleaseCollector = new ProjectReleaseCollector();

    final Map<ArtifactKey, ProjectDependency> externalExtensionDeps = new HashMap<>();

    private final DecomposedBom platformBom;

    private Map<ArtifactKey, PlatformMember> members = new HashMap<>();

    private PlatformBomConfig config;

    private VersionConstraintComparator versionComparator;

    private PlatformMember memberBeingProcessed;

    private Map<ArtifactKey, Map<String, Set<String>>> commonNotManagedDeps;

    private final PlatformVersionIncrementor versionIncrementor;

    public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {
        this(config, MessageWriter.info());
    }

    public PlatformBomComposer(PlatformBomConfig config, MessageWriter logger) throws BomDecomposerException {
        this.config = config;
        this.logger = logger;
        this.resolver = config.artifactResolver();
        this.extCoordsFilterFactory = ExtensionCoordsFilterFactory.newInstance(config, logger);
        this.versionIncrementor = "pnc".equalsIgnoreCase(config.versionIncrementor())
                ? new PncVersionIncrementor(new SpPlatformVersionIncrementor())
                : new SpPlatformVersionIncrementor();

        final DecomposedBom originalQuarkusBom = BomDecomposer.config()
                .logger(logger)
                .mavenArtifactResolver(resolver())
                .dependencies(getOriginalConstraints(config.quarkusBom(), false))
                .bomArtifact(config.quarkusBom().getInputBom())
                .decompose();
        config.quarkusBom().setOriginalDecomposedBom(originalQuarkusBom);
        initQuarkusBomReleaseBuilders(originalQuarkusBom);

        for (ProjectRelease r : quarkusBomReleaseBuilders.values()) {
            preferredReleases.computeIfAbsent(r.id().origin(), k -> new ArrayList<>()).add(r);
        }

        for (PlatformMember member : config.externalMembers()) {
            logger.info("Decomposing " + (member.getInputBom() == null ? member.key() : member.getInputBom()));
            members.put(member.key(), member);
            DecomposedBom originalBom = BomDecomposer.config()
                    .logger(logger)
                    .mavenArtifactResolver(resolver())
                    .dependencies(getOriginalConstraints(member, true))
                    .bomArtifact(member.getInputBom() == null ? member.getConfiguredPlatformBom() : member.getInputBom())
                    .decompose();
            originalBom = ExtensionFilter.getInstance(resolver(), logger, member).transform(originalBom);
            member.setOriginalDecomposedBom(originalBom);

            if (!member.getOwnGroupIds().isEmpty()) {
                for (ProjectRelease r : originalBom.releases()) {
                    for (String groupId : r.groupIds()) {
                        if (member.getOwnGroupIds().contains(groupId)) {
                            preferredReleases.computeIfAbsent(r.id().origin(), k -> new ArrayList<>()).add(r);
                            break;
                        }
                    }
                }
            }
        }

        for (PlatformMember member : config.externalMembers()) {
            logger.info("Aligning "
                    + (member.getInputBom() == null ? member.getConfiguredPlatformBom() : member.getInputBom()));
            memberBeingProcessed = member;
            member.originalDecomposedBom().visit(this);
        }

        logger.info("Generating " + config.bomArtifact());
        platformBom = generatePlatformBom();

        config.quarkusBom().setAlignedDecomposedBom(generateQuarkusBom());
        updateMemberBoms();
    }

    private void initQuarkusBomReleaseBuilders(DecomposedBom originalQuarkusBom) throws BomDecomposerException {
        final PlatformMember quarkusBom = config.quarkusBom();
        final ExtensionFilter coreFilter = ExtensionFilter.getInstance(resolver(), logger, quarkusBom);
        final DecomposedBom filteredQuarkusBom = coreFilter.transform(originalQuarkusBom);
        for (ProjectRelease r : filteredQuarkusBom.releases()) {
            ProjectRelease.Builder releaseBuilder = null;
            for (ProjectDependency d : r.dependencies()) {
                final ProjectDependency effectiveDep = effectiveDep(d);
                if (effectiveDep != null) {
                    if (releaseBuilder == null) {
                        releaseBuilder = quarkusBomReleaseBuilders.computeIfAbsent(d.releaseId(), ProjectRelease::builder);
                    }
                    releaseBuilder.add(effectiveDep);
                    preferredDeps.put(effectiveDep.key(), effectiveDep);
                }
            }
        }
    }

    private DecomposedBom generateQuarkusBom() throws BomDecomposerException {

        final boolean checkForChanges;
        if (config.quarkusBom().isIncrementBomVersionOnChange() && config.quarkusBom().previousLastUpdatedBom() != null) {
            var configuredVersion = RhVersionPattern
                    .ensureNoRhQualifier(config.quarkusBom().getConfiguredPlatformBom().getVersion());
            var prevVersion = RhVersionPattern.ensureNoRhQualifier(config.quarkusBom().previousLastUpdatedBom().getVersion());
            checkForChanges = new DefaultArtifactVersion(prevVersion)
                    .compareTo(new DefaultArtifactVersion(configuredVersion)) >= 0;
        } else {
            checkForChanges = false;
        }

        final Set<ArtifactCoords> previousManagedDeps;
        if (checkForChanges) {
            logger.debug("Checking for changes %s", config.quarkusBom().previousLastUpdatedBom());
            var managedDeps = resolver().describe(config.quarkusBom().previousLastUpdatedBom()).getManagedDependencies();
            previousManagedDeps = new HashSet<>(managedDeps.size());
            for (var d : managedDeps) {
                var a = d.getArtifact();
                if (isMeaningfulClasspathConstraint(a)) {
                    previousManagedDeps.add(ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(),
                            a.getExtension(), a.getVersion()));
                }
            }
        } else {
            previousManagedDeps = null;
        }

        final DecomposedBom.Builder quarkusBomBuilder = DecomposedBom.builder()
                .bomSource(PomSource.of(config.quarkusBom().getConfiguredPlatformBom()));
        boolean newConstraints = false;
        for (ProjectRelease.Builder rb : quarkusBomReleaseBuilders.values()) {
            final ProjectRelease release = rb.build();
            quarkusBomBuilder.addRelease(release);

            if (previousManagedDeps != null && !newConstraints) {
                for (var d : release.dependencies()) {
                    var a = d.artifact();
                    if (isMeaningfulClasspathConstraint(a)
                            && !previousManagedDeps.remove(ArtifactCoords.of(a.getGroupId(), a.getArtifactId(),
                                    a.getClassifier(), a.getExtension(), a.getVersion()))) {
                        newConstraints = true;
                    }
                }
            }
        }
        if (config.quarkusBom().isIncrementBomVersionOnChange()
                && (config.quarkusBom().previousLastUpdatedBom() == null || !checkForChanges)
                || previousManagedDeps != null && (!previousManagedDeps.isEmpty() || newConstraints)) {
            var configured = config.quarkusBom().getConfiguredPlatformBom();
            quarkusBomBuilder.bomArtifact(new DefaultArtifact(
                    configured.getGroupId(), configured.getArtifactId(), configured.getClassifier(), configured.getExtension(),
                    incrementVersion(config.quarkusBom(), checkForChanges)));
            if (previousManagedDeps != null && (!previousManagedDeps.isEmpty() || newConstraints)) {
                logger.info("Bumped version of %s to %s", config.quarkusBom().getConfiguredPlatformBom(),
                        quarkusBomBuilder.getBomArtifact());
            }
        } else {
            quarkusBomBuilder.bomArtifact(checkForChanges ? config.quarkusBom().previousLastUpdatedBom()
                    : config.quarkusBom().getConfiguredPlatformBom());
        }

        addPlatformArtifacts(config.quarkusBom(), quarkusBomBuilder);

        return quarkusBomBuilder.build();
    }

    public DecomposedBom platformBom() {
        return platformBom;
    }

    private void updateMemberBoms() {
        for (PlatformMember member : members.values()) {
            final boolean checkForChanges;
            if (member.isIncrementBomVersionOnChange() && member.previousLastUpdatedBom() != null) {
                var configuredVersion = RhVersionPattern.ensureNoRhQualifier(member.getConfiguredPlatformBom().getVersion());
                var prevVersion = RhVersionPattern.ensureNoRhQualifier(member.previousLastUpdatedBom().getVersion());
                checkForChanges = new DefaultArtifactVersion(prevVersion)
                        .compareTo(new DefaultArtifactVersion(configuredVersion)) >= 0;
            } else {
                checkForChanges = false;
            }
            final Set<ArtifactCoords> previousManagedDeps;
            if (checkForChanges) {
                logger.debug("Checking for changes %s", member.previousLastUpdatedBom());
                var managedDeps = resolver().describe(member.previousLastUpdatedBom()).getManagedDependencies();
                previousManagedDeps = new HashSet<>(managedDeps.size());
                for (var d : managedDeps) {
                    var a = d.getArtifact();
                    if (isMeaningfulClasspathConstraint(a)) {
                        previousManagedDeps.add(ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(),
                                a.getExtension(), a.getVersion()));
                    }
                }
            } else {
                previousManagedDeps = null;
            }

            final Map<ReleaseId, ProjectRelease.Builder> releaseBuilders = new HashMap<>();
            final AtomicBoolean newConstraints = new AtomicBoolean();
            acceptOriginalMemberConstraints(member, dep -> {
                ProjectDependency platformDep = preferredDeps.get(dep.key());
                if (platformDep == null) {
                    platformDep = externalExtensionDeps.get(dep.key());
                }
                if (platformDep == null) {
                    throw new IllegalStateException("Failed to locate " + dep.key() + " in the generated platform BOM");
                }
                if (member.config().isKeepThirdpartyExclusions()
                        && !dep.dependency().getExclusions().equals(platformDep.dependency().getExclusions())) {
                    platformDep = ProjectDependency.create(platformDep.releaseId(), new Dependency(platformDep.artifact(),
                            dep.dependency().getScope(), dep.dependency().isOptional(), dep.dependency().getExclusions()));
                }
                releaseBuilders.computeIfAbsent(platformDep.releaseId(), ProjectRelease::builder).add(platformDep);
                if (previousManagedDeps != null && !newConstraints.get()) {
                    var a = platformDep.artifact();
                    if (isMeaningfulClasspathConstraint(a)
                            && !previousManagedDeps.remove(ArtifactCoords.of(a.getGroupId(), a.getArtifactId(),
                                    a.getClassifier(), a.getExtension(), a.getVersion()))) {
                        newConstraints.set(true);
                    }
                }
            });

            final Artifact generatedBomArtifact;
            if (member.isIncrementBomVersionOnChange() && (member.previousLastUpdatedBom() == null || !checkForChanges)
                    || previousManagedDeps != null && (!previousManagedDeps.isEmpty() || newConstraints.get())) {
                var configured = member.getConfiguredPlatformBom();
                generatedBomArtifact = new DefaultArtifact(
                        configured.getGroupId(), configured.getArtifactId(), configured.getClassifier(),
                        configured.getExtension(),
                        incrementVersion(member, checkForChanges));
                if (previousManagedDeps != null && (!previousManagedDeps.isEmpty() || newConstraints.get())) {
                    logger.info("Bumped version of %s to %s", member.previousLastUpdatedBom(),
                            generatedBomArtifact.getVersion());
                }
            } else {
                generatedBomArtifact = checkForChanges ? member.previousLastUpdatedBom() : member.getConfiguredPlatformBom();
            }

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

    private void acceptOriginalMemberConstraints(PlatformMember member, Consumer<ProjectDependency> consumer) {
        for (ProjectRelease release : member.originalDecomposedBom().releases()) {
            for (ProjectDependency dep : release.dependencies()) {
                if (config.excluded(dep.key())) {
                    continue;
                }
                consumer.accept(dep);
            }
        }
    }

    private void acceptConstraints(Collection<ProjectRelease.Builder> releaseBuilders, Consumer<ProjectDependency> consumer)
            throws BomDecomposerException {
        for (ProjectRelease.Builder b : releaseBuilders) {
            for (ProjectDependency dep : b.dependencies()) {
                consumer.accept(dep);
            }
        }
    }

    private void addPlatformArtifacts(PlatformMember memberConfig,
            final DecomposedBom.Builder updatedBom) {
        final Artifact generatedBomArtifact = updatedBom.getBomArtifact();
        if (!generatedBomArtifact.equals(memberConfig.getInputBom())) {
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

    private ExtensionInfo getExtensionInfoOrNull(Artifact a) {
        File f = a.getFile();
        if (f == null) {
            try {
                f = resolver().resolve(a).getArtifact().getFile();
            } catch (ArtifactNotFoundException e) {
                return null;
            }
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

        for (ProjectDependency dep : preferredDeps.values()) {
            dep = effectiveDep(dep);
            if (dep == null) {
                continue;
            }
            platformReleaseBuilders.computeIfAbsent(dep.releaseId(), ProjectRelease::builder).add(dep);
        }

        for (Collection<ProjectRelease> releases : extReleaseCollector.getOriginReleaseBuilders()) {

            if (releases.size() == 1) {
                mergeExtensionDeps(releases.iterator().next());
                continue;
            }

            // pick the preferred version for each dependency
            final Map<ArtifactKey, ProjectDependency> preferredReleaseDeps = new HashMap<>();
            for (ProjectRelease release : releases) {
                for (ProjectDependency dep : release.dependencies()) {
                    preferredReleaseDeps.merge(dep.key(), dep, (current, other) -> {
                        if (versionConstraintComparator().compare(new DefaultArtifactVersion(current.artifact().getVersion()),
                                new DefaultArtifactVersion(other.artifact().getVersion())) > 0) {
                            return current;
                        }
                        return other;
                    });
                }
            }

            // align each dependency from the origin to the preferred version
            final LinkedHashMap<String, ReleaseId> preferredVersions = preferredVersions(releases);
            for (ProjectDependency dep : preferredReleaseDeps.values()) {
                final ProjectDependency preferredDep = preferredDeps.get(dep.key());
                if (preferredDep != null && preferredDep.artifact().getVersion().equals(dep.artifact().getVersion())) {
                    continue;
                }
                addNonQuarkusDep(getPreferredDependencyVersion(dep, preferredVersions));
            }
        }

        for (ProjectDependency dep : externalExtensionDeps.values()) {
            platformReleaseBuilders.computeIfAbsent(dep.releaseId(), ProjectRelease::builder).add(dep);
        }

        // TODO align common not managed deps
        logCommonNotManagedDeps(platformReleaseBuilders);

        final DecomposedBom.Builder platformBuilder = DecomposedBom.builder().bomArtifact(config.bomArtifact())
                .bomSource(config.bomResolver());
        for (ProjectRelease.Builder builder : platformReleaseBuilders.values()) {
            platformBuilder.addRelease(builder.build());
        }
        return platformBuilder.build();
    }

    private ProjectDependency getPreferredDependencyVersion(ProjectDependency dep,
            LinkedHashMap<String, ReleaseId> preferredVersions) {
        for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
            if (dep.artifact().getVersion().equals(preferredVersion.getKey())) {
                return dep;
            }
            if (config.isDisableGroupAlignmentToPreferredVersions()
                    && versionConstraintComparator().isPreferredVersion(preferredVersion.getKey())) {
                // we still want to re-align to a more recent upstream
                continue;
            }
            final Artifact artifact = dep.artifact().setVersion(preferredVersion.getKey());
            if (resolver().resolveOrNull(artifact) != null) {
                dep = ProjectDependency.create(preferredVersion.getValue(),
                        dep.dependency().setArtifact(artifact));
                break;
            }
        }
        return dep;
    }

    private void logCommonNotManagedDeps(Map<ReleaseId, ProjectRelease.Builder> releaseBuilders)
            throws BomDecomposerException {
        final String logCommonNotManagedDeps = System.getProperty(LOG_COMMON_NOT_MANAGED_DEPS);
        if (logCommonNotManagedDeps == null
                || !logCommonNotManagedDeps.isEmpty() && !Boolean.parseBoolean(logCommonNotManagedDeps)) {
            return;
        }
        logger.info("Collecting extension common not managed dependencies");
        commonNotManagedDeps = new HashMap<>();

        final Map<ArtifactKey, ProjectDependency> universeConstraints = new HashMap<>();
        for (ProjectRelease.Builder r : releaseBuilders.values()) {
            r.dependencies().forEach(d -> universeConstraints.put(ArtifactKey.of(d.artifact().getGroupId(),
                    d.artifact().getArtifactId(), d.artifact().getClassifier(), d.artifact().getExtension()), d));
        }

        collectNotManagedExtensionDeps(config.quarkusBom(), universeConstraints);
        for (PlatformMember member : config.externalMembers()) {
            collectNotManagedExtensionDeps(member, universeConstraints);
        }

        final Map<ReleaseOrigin, ProjectRelease.Builder> releaseBuildersByOrigin = new HashMap<>(releaseBuilders.size());
        for (Map.Entry<ReleaseId, ProjectRelease.Builder> e : releaseBuilders.entrySet()) {
            releaseBuildersByOrigin.put(e.getKey().origin(), e.getValue());
        }

        for (Map.Entry<ArtifactKey, Map<String, Set<String>>> e : commonNotManagedDeps.entrySet()) {
            if (e.getValue().size() == 1 || universeConstraints.containsKey(e.getKey())) {
                continue;
            }

            final Set<String> strVersions = e.getValue().keySet();
            final List<ArtifactVersion> versions = new ArrayList<>(strVersions.size());
            for (String s : strVersions) {
                versions.add(new DefaultArtifactVersion(s));
            }
            Collections.sort(versions);

            for (ArtifactVersion v : versions) {
                final Path pom = resolver().resolve(new DefaultArtifact(e.getKey().getGroupId(), e.getKey().getArtifactId(),
                        null, ArtifactCoords.TYPE_POM, v.toString())).getArtifact().getFile().toPath();
                final ReleaseId releaseId;
                try {
                    releaseId = ReleaseIdFactory.forModel(ModelUtils.readModel(pom));
                } catch (IOException e1) {
                    throw new BomDecomposerException("Failed to determine the release ID for " + pom, e1);
                }
                final ProjectRelease.Builder rb = releaseBuildersByOrigin.get(releaseId.origin());
                if (rb != null) {
                    logger.info("NON-MANAGED FAMILY " + e.getKey() + ":" + v);
                    if (rb.id().equals(releaseId)) {
                        logger.info("  release id match");
                        break;
                    }
                    if (rb.artifactVersions().contains(v.toString())) {
                        logger.info("  version match");
                        break;
                    }
                    logger.info("  not matched " + rb.artifactVersions());
                }
            }

            logger.info(e.getKey().toGacString());
            for (Map.Entry<String, Set<String>> s : e.getValue().entrySet()) {
                final StringBuilder buf = new StringBuilder();
                buf.append("  ").append(s.getKey()).append(": ");
                final List<String> list = new ArrayList<>(s.getValue());
                Collections.sort(list);
                buf.append(list.get(0));
                for (int i = 1; i < list.size(); ++i) {
                    buf.append(", ").append(list.get(i));
                }
                logger.info(buf.toString());
            }
        }
    }

    private void collectNotManagedExtensionDeps(PlatformMember member, Map<ArtifactKey, ProjectDependency> universalConstraints)
            throws BomDecomposerException {
        final List<Dependency> combinedConstraints = new ArrayList<>();
        final List<ProjectDependency> memberSpecificConstraints = new ArrayList<>();
        final Set<ArtifactKey> constraintKeys = new HashSet<>();
        for (ProjectRelease.Builder r : quarkusBomReleaseBuilders.values()) {
            r.dependencies().forEach(d -> {
                combinedConstraints.add(d.dependency());
                constraintKeys.add(ArtifactKey.of(d.artifact().getGroupId(), d.artifact().getArtifactId(),
                        d.artifact().getClassifier(), d.artifact().getExtension()));
            });
        }

        final List<String> extensionGroupIds = member.getExtensionGroupIds();
        final Consumer<ProjectDependency> c = new Consumer<>() {
            @Override
            public void accept(ProjectDependency dep) {
                final Artifact a = dep.artifact();
                if (!extensionGroupIds.isEmpty() && !extensionGroupIds.contains(a.getGroupId())
                        || !a.getExtension().equals(ArtifactCoords.TYPE_JAR)
                        || a.getArtifactId().endsWith("-deployment")
                        || a.getClassifier().equals("javadoc")
                        || a.getClassifier().equals("sources")
                        || a.getClassifier().equals("tests")
                        || dep.dependency().getScope().equals("test")) {
                    return;
                }
                final ExtensionInfo ext = getExtensionInfoOrNull(a);
                if (ext == null) {
                    return;
                }
                collectNotManagedDependencies(collectDependencies(a, combinedConstraints).getChildren(), constraintKeys, member,
                        a);
                collectNotManagedDependencies(collectDependencies(ext.getDeployment(), combinedConstraints).getChildren(),
                        constraintKeys, member, ext.getDeployment());
            }
        };

        if (config.quarkusBom() != member) {
            acceptOriginalMemberConstraints(member, d -> {
                if (constraintKeys.add(d.key())) {
                    final ProjectDependency alignedDep = universalConstraints.get(d.key());
                    combinedConstraints.add(alignedDep.dependency());
                    memberSpecificConstraints.add(alignedDep);
                }
            });
            memberSpecificConstraints.forEach(c);
        } else {
            acceptConstraints(quarkusBomReleaseBuilders.values(), c);
        }
    }

    private void collectNotManagedDependencies(Collection<DependencyNode> depNodes, Set<ArtifactKey> constraints,
            PlatformMember member, Artifact root) {
        for (DependencyNode node : depNodes) {
            collectNotManagedDependencies(node.getChildren(), constraints, member, root);
            final Artifact a = node.getArtifact();
            final ArtifactKey key = ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension());
            if (constraints.contains(key)) {
                continue;
            }
            commonNotManagedDeps.computeIfAbsent(key, k -> new HashMap<>())
                    .computeIfAbsent(a.getVersion(), k -> new HashSet<>()).add(member.config().getName());
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

    private ProjectDependency effectiveDep(ProjectDependency dep) {
        if (config.excluded(dep.key())) {
            return null;
        }
        final Artifact enforced = config.enforced(dep.key());
        return enforced == null ? dep : ProjectDependency.create(dep.releaseId(), dep.dependency().setArtifact(enforced));
    }

    private void mergeExtensionDeps(ProjectRelease release)
            throws BomDecomposerException {
        for (ProjectDependency dep : release.dependencies()) {
            // the origin may have changed in the release of the dependency
            final ProjectDependency previous = preferredDeps.get(dep.key());
            if (previous != null) {
                ensurePreferredVersionUsed(null, previous, dep);
                continue;
            }
            addNonQuarkusDep(dep);
        }
    }

    private void addNonQuarkusDep(ProjectDependency dep) {
        if (config.excluded(dep.key())) {
            return;
        }
        final Artifact enforced = config.enforced(dep.key());
        if (enforced != null) {
            if (!externalExtensionDeps.containsKey(dep.key())) {
                externalExtensionDeps.put(dep.key(), ProjectDependency.create(dep.releaseId(), enforced));
            }
            return;
        }
        final ProjectDependency currentDep = externalExtensionDeps.get(dep.key());
        if (currentDep != null) {
            final ArtifactVersion currentVersion = new DefaultArtifactVersion(currentDep.artifact().getVersion());
            final ArtifactVersion newVersion = new DefaultArtifactVersion(dep.artifact().getVersion());
            if (versionConstraintComparator().compare(currentVersion, newVersion) < 0) {
                externalExtensionDeps.put(dep.key(), dep);
            }
        } else {
            externalExtensionDeps.put(dep.key(), dep);
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
        preferredReleaseByVersion = null;
        preferredVersions.clear();
        final Collection<ProjectRelease> releases = preferredReleases.get(releaseOrigin);
        if (releases != null) {
            releases.forEach(r -> preferredVersions.add(r.id().version()));
        }
        return true;
    }

    @Override
    public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
    }

    @Override
    public void visitProjectRelease(ProjectRelease release) throws BomDecomposerException {
        if (preferredVersions.isEmpty()) {
            final ProjectRelease.Builder releaseBuilder = extReleaseCollector.getOrCreateReleaseBuilder(release.id(),
                    memberBeingProcessed);
            for (ProjectDependency dep : release.dependencies()) {
                releaseBuilder.add(dep, (dep1, dep2) -> {
                    final VersionConstraintComparator vc = versionConstraintComparator();
                    if (!vc.hasVersionPreferences()) {
                        return null;
                    }
                    if (vc.isPreferredVersion(dep1)) {
                        return vc.isPreferredVersion(dep2) ? null : dep1;
                    }
                    return vc.isPreferredVersion(dep2) ? dep2 : null;
                });
            }
            return;
        }
        if (preferredVersions.contains(release.id().version())) {
            for (ProjectDependency dep : release.dependencies()) {
                final ProjectDependency previousPreference = preferredDeps.putIfAbsent(dep.key(), dep);
                if (previousPreference != null) {
                    ensurePreferredVersionUsed(memberBeingProcessed, dep, previousPreference);
                }
            }
            return;
        }
        final LinkedHashMap<String, ReleaseId> preferredVersions = getPreferredVersions(release.id().origin());
        for (final ProjectDependency memberDep : release.dependencies()) {
            ProjectDependency currentPreference = memberDep;
            if (!preferredVersions.containsKey(memberDep.artifact().getVersion())) {
                for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
                    if (config.isDisableGroupAlignmentToPreferredVersions()
                            && versionConstraintComparator().isPreferredVersion(preferredVersion.getKey())) {
                        // we still want to re-align to a more recent upstream
                        continue;
                    }
                    final Artifact artifact = memberDep.artifact().setVersion(preferredVersion.getKey());
                    if (resolver().resolveOrNull(artifact) != null) {
                        currentPreference = ProjectDependency.create(preferredVersion.getValue(),
                                memberDep.dependency().setArtifact(artifact));
                        break;
                    }
                }
            }
            final ProjectDependency previousPreference = preferredDeps.putIfAbsent(currentPreference.key(), currentPreference);
            if (previousPreference != null) {
                ensurePreferredVersionUsed(memberBeingProcessed, memberDep, currentPreference);
            }
        }
    }

    private LinkedHashMap<String, ReleaseId> getPreferredVersions(ReleaseOrigin origin) {
        if (preferredReleaseByVersion == null) {
            final Collection<ProjectRelease> releases = preferredReleases.get(origin);
            if (releases != null) {
                preferredReleaseByVersion = preferredVersions(releases);
            }
        }
        return preferredReleaseByVersion;
    }

    private void ensurePreferredVersionUsed(PlatformMember member, ProjectDependency memberDep,
            ProjectDependency currentPreference)
            throws BomDecomposerException {
        if (!currentPreference.artifact().getVersion().equals(memberDep.artifact().getVersion())
                && versionConstraintComparator().hasVersionPreferences()
                && versionConstraintComparator().isPreferredVersion(memberDep)
                && !versionConstraintComparator().isPreferredVersion(currentPreference)) {

            final StringBuilder sb = new StringBuilder();
            sb.append("Preferred constraint ").append(memberDep.artifact());
            if (member != null) {
                sb.append(" from ").append(member.config().getName());
            }

            if (ForeignPreferredConstraint.isAcceptIfCompatible(config.foreignPreferredConstraint())
                    && (memberDep.artifact().getVersion().startsWith(currentPreference.artifact().getVersion())
                            || versionsAppearToMatch(memberDep.artifact().getVersion(),
                                    currentPreference.artifact().getVersion()))) {
                sb.append(" was accepted in favor of ").append(currentPreference.artifact()).append(" owned by ");
                preferredDeps.put(memberDep.key(), memberDep);
                ProjectRelease.Builder rb = quarkusBomReleaseBuilders.get(currentPreference.releaseId());
                if (rb != null) {
                    final ProjectDependency preferredDep = ProjectDependency.create(currentPreference.releaseId(),
                            currentPreference.dependency().setArtifact(memberDep.artifact()));
                    rb.add(preferredDep, (dep1, dep2) -> preferredDep);
                    sb.append("the quarkus-bom");
                } else {
                    extReleaseCollector.enforce(memberDep, currentPreference);
                    sb.append("another member");
                }
                logger.warn(sb.toString());
                return;
            }
            sb.append(" was rejected in favor of ").append(currentPreference.artifact()).append(" owned by another member");
            if (ForeignPreferredConstraint.isError(config.foreignPreferredConstraint())) {
                throw new BomDecomposerException(sb.toString());
            }
            if (ForeignPreferredConstraint.isWarn(config.foreignPreferredConstraint())) {
                logger.warn(sb.toString());
            }
        }
    }

    private boolean versionsAppearToMatch(String preferredVersion, String currentVersion) {
        // the reason behind this method is to compare versions "normalized" by pnc
        // that ensures major.minor.micro parts are explicitly present in the version string
        // while in the upstream equivalent, for example, a the micro part could be missing
        if (RhVersionPattern.isRhVersion(preferredVersion)) {
            final ArtifactVersion preferredAv = new DefaultArtifactVersion(
                    RhVersionPattern.ensureNoRhQualifier(preferredVersion));
            final ArtifactVersion currentAv = new DefaultArtifactVersion(currentVersion);
            // qualifiers aren't parsed correctly when the micro version isn't present,
            // so we can't use equals reliably to compare them
            final String preferredQualifier = preferredAv.getQualifier() == null ? "null" : preferredAv.getQualifier();
            final String currentQualifier = currentAv.getQualifier() == null ? "null" : currentAv.getQualifier();
            return preferredAv.getMajorVersion() == currentAv.getMajorVersion()
                    && preferredAv.getMinorVersion() == currentAv.getMinorVersion()
                    && preferredAv.getIncrementalVersion() == currentAv.getIncrementalVersion()
                    && preferredAv.getBuildNumber() == currentAv.getBuildNumber()
                    && currentQualifier.startsWith(preferredQualifier);
        }
        return false;
    }

    @Override
    public void leaveBom() throws BomDecomposerException {
    }

    private VersionConstraintComparator versionConstraintComparator() {
        if (versionComparator == null) {
            final List<Pattern> preferences;
            if (config.versionConstraintPreferences().isEmpty()) {
                preferences = List.of();
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
                    // unless it's test-jars ignore test scoped constraints
                    if ("test".equals(dep.getScope()) && !"tests".equals(artifact.getClassifier())) {
                        continue;
                    }
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
                    result = new HashMap<>();
                }
                result.put(key(d.getArtifact()), d);
            }
        }
        return result.values();
    }

    private static ArtifactKey key(Artifact artifact) {
        return ArtifactKey.of(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
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

    private static boolean isMeaningfulClasspathConstraint(final Artifact a) {
        return a.getExtension().equals(ArtifactCoords.TYPE_JAR)
                && !(PlatformArtifacts.isCatalogArtifactId(a.getArtifactId())
                        || a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                        || "sources".equals(a.getClassifier())
                        || "javadoc".equals(a.getClassifier()));
    }

    private String incrementVersion(PlatformMember member, boolean takePreviousVersionIntoAccount) {
        return versionIncrementor.nextVersion(
                member.getConfiguredPlatformBom().getGroupId(),
                member.getConfiguredPlatformBom().getArtifactId(),
                member.getConfiguredPlatformBom().getVersion(),
                !takePreviousVersionIntoAccount || member.previousLastUpdatedBom() == null ? null
                        : member.previousLastUpdatedBom().getVersion());
    }
}
