package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposer.BomDecomposerConfig;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.maven.platformgen.DependenciesToBuildConfig;
import io.quarkus.bom.decomposer.maven.platformgen.PlatformConfig;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.paths.PathTree;
import io.quarkus.registry.CatalogMergeUtility;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.ExtensionOrigin;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Logs artifact coordinates (one per line) that represent supported Quarkus extensions and their dependencies
 * down to a certain depth level that need to be built from source.
 * <p>
 * The goal exposes other options that enable logging extra information, however all the extra info will be logged
 * with `#` prefix, which the tools parsing the output could treat as a comment and ignore.
 *
 */
@Mojo(name = "dependencies-to-build", threadSafe = true, requiresProject = false)
public class DependenciesToBuildMojo extends AbstractMojo {

    private static final String NOT_MANAGED = " [not managed]";

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(required = false, defaultValue = "${project.file}")
    File projectFile;

    /**
     * Coordinates of the BOM containing Quarkus extensions. If not provided defaults to the current project's POM
     */
    @Parameter(required = true, property = "bom", defaultValue = "${project.groupId}:${project.artifactId}::pom:${project.version}")
    String bom;

    /**
     * The depth level of a dependency tree of each supported Quarkus extension to capture.
     * Setting the level to 0 will target the supported extension artifacts themselves.
     * Setting the level to 1, will target the supported extension artifacts plus their direct dependencies.
     * If the level is not specified, the default will be -1, which means all the levels.
     */
    @Parameter(required = true, property = "level", defaultValue = "-1")
    int level = -1;

    /**
     * Whether to exclude dependencies (and their transitive dependencies) that aren't managed in the BOM. The default is true.
     */
    @Parameter(required = false, property = "includeNonManaged")
    boolean includeNonManaged;

    /**
     * Whether to log the coordinates of the artifacts captured down to the depth specified. The default is true.
     */
    @Parameter(required = false, property = "logArtifactsToBuild")
    boolean logArtifactsToBuild = true;

    /**
     * Whether to log the module GAVs the artifacts to be built belongs to instead of all
     * the complete artifact coordinates to be built.
     * If this option is enabled, it overrides {@link #logArtifactsToBuild}
     */
    @Parameter(required = false, property = "logModulesToBuild")
    boolean logModulesToBuild;

    /**
     * Whether to log the dependency trees walked down to the depth specified. The default is false.
     */
    @Parameter(required = false, property = "logTrees")
    boolean logTrees;

    /**
     * Whether to log the coordinates of the artifacts below the depth specified. The default is false.
     */
    @Parameter(required = false, property = "logRemaining")
    boolean logRemaining;
    /**
     * Whether to log the summary at the end. The default is true.
     */
    @Parameter(required = false, property = "logSummary")
    boolean logSummary = true;
    /**
     * Whether to log the summary at the end. The default is true.
     */
    @Parameter(required = false, property = "logNonManagedVisited")
    boolean logNonManagedVisited;

    /**
     * If specified, this parameter will cause the output to be written to the path specified, instead of writing to
     * the console.
     */
    @Parameter(property = "outputFile", required = false)
    File outputFile;
    private PrintStream fileOutput;

    /**
     * Whether to append outputs into the output file or overwrite it.
     */
    @Parameter(property = "appendOutput", required = false, defaultValue = "false")
    boolean appendOutput;

    /*
     * Whether to log code repository info for the artifacts to be built from source
     */
    @Parameter(property = "logCodeRepos", required = false)
    boolean logCodeRepos;

    /*
     * Whether to log code repository dependency graph.
     */
    @Parameter(property = "logCodeRepoGraph", required = false)
    boolean logCodeRepoGraph;

    /*
     * Whether to include parent POMs into the list to be built from source
     */
    @Parameter(property = "excludeParentPoms", required = false)
    boolean excludeParentPoms;

    @Parameter(required = false)
    PlatformConfig platformConfig;

    @Parameter(required = false)
    DependenciesToBuildConfig dependenciesToBuild;

    private Set<ArtifactCoords> targetBomConstraints;
    private Map<ArtifactCoords, List<Dependency>> enforcedConstraintsForBom = new HashMap<>();
    private final Set<ArtifactCoords> allDepsToBuild = new HashSet<>();
    private final Set<ArtifactCoords> nonManagedVisited = new HashSet<>();
    private final Set<ArtifactCoords> skippedDeps = new HashSet<>();
    private final Set<ArtifactCoords> remainingDeps = new HashSet<>();

    private final Map<ArtifactCoords, ArtifactDependency> artifactDeps = new HashMap<>();
    private final Map<ReleaseId, ReleaseRepo> releaseRepos = new HashMap<>();

    private MavenArtifactResolver resolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (logCodeRepoGraph) {
            logCodeRepos = true;
        }

        ArtifactCoords targetBomCoords = ArtifactCoords.fromString(bom);
        if (PlatformArtifacts.isCatalogArtifactId(targetBomCoords.getArtifactId())) {
            targetBomCoords = ArtifactCoords.pom(targetBomCoords.getGroupId(),
                    PlatformArtifacts.ensureBomArtifactId(targetBomCoords.getArtifactId()), targetBomCoords.getVersion());
        } else if (!targetBomCoords.getType().equals(ArtifactCoords.TYPE_POM)) {
            targetBomCoords = ArtifactCoords.pom(targetBomCoords.getGroupId(), targetBomCoords.getArtifactId(),
                    targetBomCoords.getVersion());
        }
        debug("Quarkus platform BOM %s", targetBomCoords);
        final ArtifactCoords catalogCoords = ArtifactCoords.of(targetBomCoords.getGroupId(),
                PlatformArtifacts.ensureCatalogArtifactId(targetBomCoords.getArtifactId()), targetBomCoords.getVersion(),
                "json",
                targetBomCoords.getVersion());
        debug("Quarkus extension catalog %s", catalogCoords);

        try {
            resolver = MavenArtifactResolver.builder()
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setWorkspaceDiscovery(projectFile != null)
                    .setPreferPomsFromWorkspace(projectFile != null)
                    .setCurrentProject(projectFile == null ? null : projectFile.toString())
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }

        ExtensionCatalog catalog = resolveCatalog(catalogCoords);

        final List<Dependency> targetBomManagedDeps = getBomConstraints(targetBomCoords);
        targetBomConstraints = new HashSet<>(targetBomManagedDeps.size());
        for (Dependency d : targetBomManagedDeps) {
            targetBomConstraints.add(toCoords(d.getArtifact()));
        }

        final Collection<ArtifactCoords> otherDescriptorCoords = getOtherMemberDescriptorCoords(catalog);
        if (!otherDescriptorCoords.isEmpty()) {
            ArtifactCoords generatedCoreBomCoords = null;
            if (targetBomCoords.getArtifactId().equals("quarkus-bom")) {
                generatedCoreBomCoords = targetBomCoords;
            } else {
                final String coreDescriptorArtifactId = "quarkus-bom"
                        + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX;
                for (ArtifactCoords c : otherDescriptorCoords) {
                    if (c.getArtifactId().equals(coreDescriptorArtifactId)) {
                        generatedCoreBomCoords = PlatformArtifacts.ensureBomArtifact(c);
                        break;
                    }
                }
                if (generatedCoreBomCoords == null) {
                    throw new MojoExecutionException("Failed to locate quarkus-bom among " + otherDescriptorCoords);
                }
            }
            if (targetBomCoords.equals(generatedCoreBomCoords)) {

                enforcedConstraintsForBom.put(targetBomCoords, targetBomManagedDeps);

                final List<ExtensionCatalog> catalogs = new ArrayList<>(otherDescriptorCoords.size() + 1);
                catalogs.add(catalog);
                for (ArtifactCoords descrCoords : otherDescriptorCoords) {
                    final ExtensionCatalog otherCatalog = resolveCatalog(descrCoords);
                    catalogs.add(otherCatalog);

                    final ArtifactCoords otherBomCoords = otherCatalog.getBom();
                    final List<Dependency> otherBomDeps = getBomConstraints(otherBomCoords);
                    final List<Dependency> enforcedConstraints = new ArrayList<>(
                            targetBomManagedDeps.size() + otherBomDeps.size());
                    enforcedConstraints.addAll(otherBomDeps);
                    enforcedConstraints.addAll(otherBomDeps);
                    enforcedConstraintsForBom.put(otherBomCoords, enforcedConstraints);
                }
                catalog = CatalogMergeUtility.merge(catalogs);
            } else {
                final List<Dependency> bomConstraints = getBomConstraints(generatedCoreBomCoords);
                bomConstraints.addAll(targetBomManagedDeps);
                enforcedConstraintsForBom.put(targetBomCoords, bomConstraints);
            }
        } else {
            enforcedConstraintsForBom.put(targetBomCoords, targetBomManagedDeps);
        }

        final List<Extension> supported = new ArrayList<>();
        for (Extension ext : catalog.getExtensions()) {
            ArtifactCoords rtArtifact = ext.getArtifact();
            if (isExcluded(rtArtifact)) {
                continue;
            }
            Object o = ext.getMetadata().get("redhat-support");
            if (o == null) {
                continue;
            }
            supported.add(ext);

            final List<Dependency> extConstraints = getConstraintsForExtension(ext);

            processExtensionArtifact(extConstraints,
                    ArtifactCoords.of(rtArtifact.getGroupId(), rtArtifact.getArtifactId(),
                            rtArtifact.getClassifier(), rtArtifact.getType(), rtArtifact.getVersion()));

            ArtifactCoords deploymentCoords = ArtifactCoords.jar(rtArtifact.getGroupId(),
                    rtArtifact.getArtifactId() + "-deployment", rtArtifact.getVersion());
            if (!targetBomConstraints.contains(deploymentCoords)) {
                final Path rtJar;
                try {
                    rtJar = resolver.resolve(toAetherArtifact(rtArtifact)).getArtifact().getFile().toPath();
                } catch (BootstrapMavenException e1) {
                    throw new MojoExecutionException("Failed to resolve " + rtArtifact, e1);
                }
                deploymentCoords = PathTree.ofDirectoryOrArchive(rtJar).apply(BootstrapConstants.DESCRIPTOR_PATH, visit -> {
                    if (visit == null) {
                        return null;
                    }
                    final Properties props = new Properties();
                    try (BufferedReader reader = Files.newBufferedReader(visit.getPath())) {
                        props.load(reader);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    final String str = props.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
                    return str == null ? null : ArtifactCoords.fromString(str);
                });
                if (deploymentCoords == null) {
                    throw new MojoExecutionException(
                            "Failed to determine the corresponding deployment artifact for " + rtArtifact.toCompactCoords());
                }
            }
            processExtensionArtifact(extConstraints, deploymentCoords);
        }

        if (dependenciesToBuild != null && !dependenciesToBuild.getIncludeArtifacts().isEmpty()) {
            // this is a tricky case, these artifacts will be resolved against all the member BOMs
            // we may want to have them configured per member instead of in the global config
            final List<Dependency> enforcedConstraints = enforcedConstraintsForBom.get(targetBomCoords);
            if (enforcedConstraints == null) {
                throw new MojoExecutionException("Failed to locate enforced constraints for " + targetBomCoords);
            }
            for (ArtifactCoords coords : dependenciesToBuild.getIncludeArtifacts()) {
                processExtensionArtifact(enforcedConstraints, coords);
            }
        }

        try {
            int codeReposTotal = 0;
            if (logArtifactsToBuild && !allDepsToBuild.isEmpty()) {
                logComment("Artifacts to be built from source from " + targetBomCoords.toCompactCoords() + ":");
                if (logCodeRepos) {
                    final Artifact bomArtifact = new DefaultArtifact(targetBomCoords.getGroupId(),
                            targetBomCoords.getArtifactId(),
                            ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, targetBomCoords.getVersion());
                    initReleaseRepos(decompose(bomArtifact, targetBomManagedDeps, nonManagedVisited));
                    codeReposTotal = releaseRepos.size();

                    final Map<ReleaseId, ReleaseRepo> orderedMap = new LinkedHashMap<>(codeReposTotal);
                    for (ReleaseRepo r : releaseRepos.values()) {
                        if (r.isRoot()) {
                            order(r, orderedMap);
                        }
                    }

                    for (ReleaseRepo e : orderedMap.values()) {
                        logComment(e.id().toString());
                        for (String s : toSortedStrings(e.artifacts, logModulesToBuild)) {
                            log(s);
                        }
                    }

                    if (logCodeRepoGraph) {
                        logComment("");
                        logComment("Code repository dependency graph");
                        for (ReleaseRepo r : releaseRepos.values()) {
                            if (r.isRoot()) {
                                logReleaseRepoDep(r, 0);
                            }
                        }
                        logComment("");
                    }

                } else {
                    for (String s : toSortedStrings(allDepsToBuild, logModulesToBuild)) {
                        log(s);
                    }
                }
            }

            if (logNonManagedVisited && !nonManagedVisited.isEmpty()) {
                logComment("Non-managed dependencies visited walking dependency trees:");
                final List<String> sorted = toSortedStrings(nonManagedVisited, logModulesToBuild);
                for (int i = 0; i < sorted.size(); ++i) {
                    logComment((i + 1) + ") " + sorted.get(i));
                }
            }

            if (logRemaining) {
                logComment("Remaining artifacts include:");
                final List<String> sorted = toSortedStrings(remainingDeps, logModulesToBuild);
                for (int i = 0; i < sorted.size(); ++i) {
                    logComment((i + 1) + ") " + sorted.get(i));
                }
            }

            if (logSummary) {
                final StringBuilder sb = new StringBuilder().append("Selecting ");
                if (this.level < 0) {
                    sb.append("all the");
                } else {
                    sb.append(this.level).append(" level(s) of");
                }
                if (this.includeNonManaged) {
                    sb.append(" managed and non-managed");
                } else {
                    sb.append(" managed (stopping at the first non-managed one)");
                }
                sb.append(" dependencies of supported extensions from ").append(targetBomCoords.toCompactCoords())
                        .append(" will result in:");
                logComment(sb.toString());

                sb.setLength(0);
                sb.append(allDepsToBuild.size()).append(" artifacts");
                if (codeReposTotal > 0) {
                    sb.append(" from ").append(codeReposTotal).append(" code repositories");
                }
                sb.append(" to build from source");
                logComment(sb.toString());
                if (includeNonManaged && !nonManagedVisited.isEmpty()) {
                    logComment("  * " + nonManagedVisited.size() + " of which is/are not managed by the BOM");
                }
                if (!skippedDeps.isEmpty()) {
                    logComment(skippedDeps.size() + " dependency nodes skipped");
                }
                logComment((allDepsToBuild.size() + skippedDeps.size()) + " dependencies visited in total");
                logComment(supported.size() + " out of " + catalog.getExtensions().size() + " extensions are supported");
            }
        } finally {
            if (fileOutput != null) {
                fileOutput.close();
            }
        }
    }

    private List<Dependency> getConstraintsForExtension(Extension ext) throws MojoExecutionException {
        for (ExtensionOrigin origin : ext.getOrigins()) {
            if (origin.getBom() == null) {
                continue;
            }
            List<Dependency> enforcedConstraints = enforcedConstraintsForBom.get(origin.getBom());
            if (enforcedConstraints != null) {
                return enforcedConstraints;
            }
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("Failed to locate enforced constraints for ").append(ext.getArtifact().toCompactCoords())
                .append(" with origins ");
        for (int i = 0; i < ext.getOrigins().size(); ++i) {
            final ExtensionOrigin origin = ext.getOrigins().get(i);
            if (origin.getBom() == null) {
                continue;
            }
            sb.append(origin.getBom().toCompactCoords());
            if (i > 0) {
                sb.append(", ");
            }
        }
        sb.append(" among ");
        var i = enforcedConstraintsForBom.keySet().iterator();
        if (i.hasNext()) {
            sb.append(i.next().toCompactCoords());
            while (i.hasNext()) {
                sb.append(", ").append(i.next().toCompactCoords());
            }
        }
        throw new MojoExecutionException(sb.toString());
    }

    private List<Dependency> getBomConstraints(ArtifactCoords bomCoords)
            throws MojoExecutionException {
        final Artifact bomArtifact = new DefaultArtifact(bomCoords.getGroupId(), bomCoords.getArtifactId(),
                ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, bomCoords.getVersion());
        List<Dependency> managedDeps;
        try {
            managedDeps = resolver.resolveDescriptor(bomArtifact)
                    .getManagedDependencies();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve the descriptor of " + bomCoords, e);
        }
        if (managedDeps.isEmpty()) {
            throw new MojoExecutionException(bomCoords.toCompactCoords()
                    + " does not include any managed dependency or its descriptor could not be read");
        }
        return managedDeps;
    }

    private ExtensionCatalog resolveCatalog(final ArtifactCoords catalogCoords) throws MojoExecutionException {
        final Path jsonPath;
        try {
            jsonPath = resolver.resolve(toAetherArtifact(catalogCoords))
                    .getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve the extension catalog", e);
        }

        ExtensionCatalog catalog;
        try {
            debug("Parsing extension catalog %s", jsonPath);
            catalog = ExtensionCatalog.fromFile(jsonPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + jsonPath, e);
        }
        return catalog;
    }

    private void processExtensionArtifact(List<Dependency> managedDeps, ArtifactCoords extArtifact) {
        final DependencyNode root;
        try {
            final Artifact a = toAetherArtifact(extArtifact);
            root = resolver.getSystem().collectDependencies(resolver.getSession(), new CollectRequest()
                    .setManagedDependencies(managedDeps)
                    .setRepositories(resolver.getRepositories())
                    .setRoot(new Dependency(a, JavaScopes.RUNTIME)))
                    .getRoot();
        } catch (DependencyCollectionException e1) {
            throw new RuntimeException("Failed to collect dependencies of " + extArtifact.toCompactCoords(), e1);
        }

        if (logTrees) {
            if (targetBomConstraints.contains(extArtifact)) {
                logComment(extArtifact.toCompactCoords());
            } else {
                logComment(extArtifact.toCompactCoords() + NOT_MANAGED);
            }
        }

        if (addToBeBuilt(extArtifact)) {
            for (DependencyNode d : root.getChildren()) {
                processNodes(logCodeRepos ? getOrCreateArtifactDep(extArtifact) : null, d, 1, false);
            }
        } else if (logRemaining) {
            for (DependencyNode d : root.getChildren()) {
                processNodes(null, d, 1, true);
            }
        }

        if (logTrees) {
            logComment("");
        }
    }

    private static DefaultArtifact toAetherArtifact(ArtifactCoords a) {
        return new DefaultArtifact(a.getGroupId(),
                a.getArtifactId(), a.getClassifier(),
                a.getType(), a.getVersion());
    }

    private DecomposedBom decompose(Artifact bomArtifact, Collection<Dependency> managed, Collection<ArtifactCoords> nonManaged)
            throws MojoExecutionException {
        final BomDecomposerConfig config = BomDecomposer.config()
                .bomArtifact(bomArtifact)
                .logger(new MojoMessageWriter(getLog()))
                .loadReleaseDetectors(false)
                .mavenArtifactResolver(ArtifactResolverProvider.get(resolver));

        if (nonManaged != null && !nonManaged.isEmpty()) {
            final List<Dependency> allDeps = new ArrayList<>(managed.size() + nonManaged.size());
            allDeps.addAll(managed);
            for (ArtifactCoords c : nonManaged) {
                allDeps.add(new Dependency(
                        toAetherArtifact(c),
                        JavaScopes.COMPILE));
            }
            config.dependencies(allDeps);
        }

        DecomposedBom decomposedBom = null;
        try {
            decomposedBom = config.decompose();
        } catch (BomDecomposerException e) {
            throw new MojoExecutionException("Failed to decompose BOM " + bomArtifact, e);
        }
        return decomposedBom;
    }

    private void initReleaseRepos(DecomposedBom decomposedBom) {
        final Map<ArtifactKey, ProjectDependency> deps = new HashMap<>();
        decomposedBom.releases().forEach(r -> {
            r.dependencies().forEach(d -> deps.put(d.key(), d));
            getOrCreateRepo(r);
        });
        final Map<ArtifactCoords, ReleaseId> artifactReleases = new HashMap<>();
        for (ArtifactCoords c : allDepsToBuild) {
            final ProjectDependency dep = deps.get(c.getKey());
            if (dep == null) {
                throw new IllegalStateException("Failed to find dependency for " + c.getKey());
            }
            getRepo(dep.releaseId()).artifacts.add(c);
            artifactReleases.put(c, dep.releaseId());
        }
        final Iterator<Map.Entry<ReleaseId, ReleaseRepo>> i = releaseRepos.entrySet().iterator();
        while (i.hasNext()) {
            if (i.next().getValue().artifacts.isEmpty()) {
                i.remove();
            }
        }
        for (ArtifactDependency d : artifactDeps.values()) {
            final ReleaseRepo repo = getRepo(artifactReleases.get(d.coords));
            for (ArtifactDependency c : d.children.values()) {
                repo.addRepoDependency(getRepo(artifactReleases.get(c.coords)));
            }
        }
    }

    private void order(ReleaseRepo repo, Map<ReleaseId, ReleaseRepo> repos) {
        for (ReleaseRepo d : repo.dependencies.values()) {
            if (repos.containsKey(d.id())) {
                continue;
            }
            order(d, repos);
        }
        repos.putIfAbsent(repo.id(), repo);
    }

    private void logReleaseRepoDep(ReleaseRepo repo, int depth) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; ++i) {
            sb.append("  ");
        }
        sb.append(repo.id().origin()).append(' ').append(repo.id().version());
        logComment(sb.toString());
        for (ReleaseRepo child : repo.dependencies.values()) {
            logReleaseRepoDep(child, depth + 1);
        }
    }

    private void debug(String msg, Object... args) {
        if (getLog().isDebugEnabled()) {
            if (args.length == 0) {
                getLog().debug(msg);
            } else {
                getLog().debug(String.format(msg, args));
            }
        }
    }

    private static List<String> toSortedStrings(Collection<ArtifactCoords> coords, boolean asModules) {
        final List<String> list;
        if (asModules) {
            final Set<String> set = new HashSet<>();
            for (ArtifactCoords c : coords) {
                set.add(c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getVersion());
            }
            list = new ArrayList<>(set);
        } else {
            list = new ArrayList<>(coords.size());
            for (ArtifactCoords c : coords) {
                list.add(c.toGACTVString());
            }
        }
        Collections.sort(list);
        return list;
    }

    private PrintStream getOutput() {
        if (outputFile == null) {
            return System.out;
        }
        if (fileOutput == null) {
            outputFile.getParentFile().mkdirs();
            try {
                fileOutput = new PrintStream(new FileOutputStream(outputFile, appendOutput), false);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Failed to open " + outputFile + " for writing", e);
            }
        }
        return fileOutput;
    }

    private void logComment(String msg) {
        log("# " + msg);
    }

    private void log(String msg) {
        getOutput().println(msg);
    }

    private void processNodes(ArtifactDependency parent, DependencyNode node, int level, boolean remaining) {
        final ArtifactCoords coords = toCoords(node.getArtifact());
        if (isExcluded(coords)) {
            return;
        }
        ArtifactDependency artDep = null;
        if (remaining) {
            addToRemaining(coords);
        } else if (this.level < 0 || level <= this.level) {
            if (addToBeBuilt(coords)) {
                if (logTrees) {
                    final StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < level; ++i) {
                        buf.append("  ");
                    }
                    buf.append(coords.toCompactCoords());
                    if (!targetBomConstraints.contains(coords)) {
                        buf.append(' ').append(NOT_MANAGED);
                    }
                    logComment(buf.toString());
                }
                if (parent != null) {
                    artDep = getOrCreateArtifactDep(coords);
                    parent.addDependency(artDep);
                }
            } else if (logRemaining) {
                remaining = true;
            } else {
                return;
            }
        } else {
            addToSkipped(coords);
            if (logRemaining) {
                remaining = true;
                addToRemaining(coords);
            } else {
                return;
            }
        }
        for (DependencyNode child : node.getChildren()) {
            processNodes(artDep, child, level + 1, remaining);
        }
    }

    private boolean addToBeBuilt(ArtifactCoords coords) {
        final boolean managed = targetBomConstraints.contains(coords);
        if (!managed) {
            nonManagedVisited.add(coords);
        }

        if (managed || includeNonManaged || isIncluded(coords)
                || !excludeParentPoms && coords.getType().equals(ArtifactCoords.TYPE_POM)) {
            allDepsToBuild.add(coords);
            skippedDeps.remove(coords);
            remainingDeps.remove(coords);
            if (!excludeParentPoms) {
                addParentPomToBeBuilt(coords);
            }
            return true;
        }

        addToSkipped(coords);
        if (logRemaining) {
            addToRemaining(coords);
        }
        return false;
    }

    private void addParentPomToBeBuilt(ArtifactCoords coords) {
        final ArtifactCoords pomCoords = coords.getType().equals(ArtifactCoords.TYPE_POM) ? coords
                : ArtifactCoords.pom(coords.getGroupId(), coords.getArtifactId(), coords.getVersion());
        if (allDepsToBuild.contains(pomCoords)) {
            return;
        }
        final Path pomXml;
        try {
            pomXml = resolver.resolve(toAetherArtifact(pomCoords)).getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new IllegalStateException("Failed to resolve " + pomCoords, e);
        }
        final Model model;
        try {
            model = ModelUtils.readModel(pomXml);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + pomXml, e);
        }
        final Parent parent = model.getParent();
        if (parent == null) {
            return;
        }
        String parentVersion = parent.getVersion();
        if (ModelUtils.isUnresolvedVersion(parentVersion)) {
            if (model.getVersion() == null || model.getVersion().equals(parentVersion)) {
                parentVersion = pomCoords.getVersion();
            } else {
                getLog().warn("Failed to resolve the version of" + parent.getGroupId() + ":" + parent.getArtifactId() + ":"
                        + parent.getVersion() + " as a parent of " + pomCoords);
                return;
            }
        }
        addToBeBuilt(ArtifactCoords.pom(parent.getGroupId(), parent.getArtifactId(), parentVersion));
    }

    private void addToSkipped(ArtifactCoords coords) {
        if (!allDepsToBuild.contains(coords)) {
            skippedDeps.add(coords);
        }
    }

    private void addToRemaining(ArtifactCoords coords) {
        if (!allDepsToBuild.contains(coords)) {
            remainingDeps.add(coords);
        }
    }

    private boolean isExcluded(ArtifactCoords coords) {
        return dependenciesToBuild != null
                && (dependenciesToBuild.getExcludeGroupIds().contains(coords.getGroupId())
                        || dependenciesToBuild.getExcludeKeys().contains(coords.getKey())
                        || dependenciesToBuild.getExcludeArtifacts().contains(coords));
    }

    private boolean isIncluded(ArtifactCoords coords) {
        return dependenciesToBuild != null
                && (dependenciesToBuild.getIncludeGroupIds().contains(coords.getGroupId())
                        || dependenciesToBuild.getIncludeKeys().contains(coords.getKey())
                        || dependenciesToBuild.getIncludeArtifacts().contains(coords));
    }

    private static ArtifactCoords toCoords(Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private ArtifactDependency getOrCreateArtifactDep(ArtifactCoords c) {
        return artifactDeps.computeIfAbsent(c, k -> new ArtifactDependency(c));
    }

    private static class ArtifactDependency {
        final ArtifactCoords coords;
        final Map<ArtifactCoords, ArtifactDependency> children = new LinkedHashMap<>();

        ArtifactDependency(ArtifactCoords coords) {
            this.coords = coords;
        }

        void addDependency(ArtifactDependency d) {
            children.putIfAbsent(d.coords, d);
        }
    }

    private ReleaseRepo getOrCreateRepo(ProjectRelease release) {
        return releaseRepos.computeIfAbsent(release.id(), k -> new ReleaseRepo(release));
    }

    private ReleaseRepo getRepo(ReleaseId id) {
        return Objects.requireNonNull(releaseRepos.get(id));
    }

    private static class ReleaseRepo {

        final ProjectRelease release;
        final List<ArtifactCoords> artifacts = new ArrayList<>();
        final Map<ReleaseId, ReleaseRepo> parents = new HashMap<>();
        final Map<ReleaseId, ReleaseRepo> dependencies = new LinkedHashMap<>();

        ReleaseRepo(ProjectRelease release) {
            this.release = release;
        }

        ReleaseId id() {
            return release.id();
        }

        void addRepoDependency(ReleaseRepo repo) {
            if (repo != this) {
                dependencies.putIfAbsent(repo.id(), repo);
                repo.parents.putIfAbsent(id(), this);
            }
        }

        boolean isRoot() {
            return parents.isEmpty();
        }
    }

    private static Collection<ArtifactCoords> getOtherMemberDescriptorCoords(ExtensionCatalog catalog) {
        Map<String, Object> map = catalog.getMetadata();
        if (map == null) {
            return List.of();
        }
        Object o = map.get("platform-release");
        if (o == null || !(o instanceof Map)) {
            return List.of();
        }
        o = ((Map<?, ?>) o).get("members");
        if (o == null || !(o instanceof List)) {
            return List.of();
        }
        final List<?> list = (List<?>) o;
        final List<ArtifactCoords> result = new ArrayList<>(list.size());
        list.forEach(i -> {
            if (!i.equals(catalog.getId())) {
                result.add(ArtifactCoords.fromString(i.toString()));
            }
        });
        return result;
    }
}
