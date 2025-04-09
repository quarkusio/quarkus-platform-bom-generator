package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.maven.platformgen.PlatformConfig;
import io.quarkus.bom.platform.ProjectDependencyFilterConfig;
import io.quarkus.bom.platform.SbomConfig;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.domino.manifest.SbomGeneratingDependencyVisitor;
import io.quarkus.domino.manifest.SbomGenerator;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.paths.PathTree;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

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
    Boolean includeNonManaged;

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
     * Comma-separated list of root artifacts to log dependency trees for
     */
    @Parameter(required = false, property = "logTreesFor")
    String logTreesFor;

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
     * Whether to exclude parent POMs from the list of artifacts to be built from source
     */
    @Parameter(property = "excludeParentPoms", required = false)
    boolean excludeParentPoms;

    /*
     * Whether to exclude BOMs imported in the POMs of artifacts to be built from the list of artifacts to be built from source
     */
    @Parameter(property = "excludeBomImports", required = false)
    boolean excludeBomImports;

    @Parameter(required = false)
    PlatformConfig platformConfig;

    @Parameter(required = false)
    ProjectDependencyFilterConfig dependenciesToBuild;

    @Parameter(required = false, property = "legacyScmLocator")
    boolean legacyScmLocator;

    @Parameter(required = false, property = "recipeRepos")
    List<String> recipeRepos;

    @Parameter(required = false, property = "warnOnMissingScm")
    boolean warnOnMissingScm;

    /*
     * Whether to include dependencies that have already been built
     */
    @Parameter(property = "includeAlreadyBuilt", required = false)
    boolean includeAlreadyBuilt;

    /**
     * Whether to generate an SBOM
     */
    @Parameter(required = false, property = "manifest")
    boolean manifest;

    /**
     * In case {@link #manifest} is enabled, this option can be used to request a specific version of the CycloneDX schema.
     * The default version will be the latest supported by the integrated CycloneDX library.
     */
    @Parameter(required = false, property = "cdxSchemaVersion")
    String cdxSchemaVersion;

    /**
     * Indicates whether to record artifact dependencies in the manifest and if so, which strategy to use.
     * Supported values are:
     * <li>none - do not record dependencies at all</li>
     * <li>tree - record default (conflicts resolved) dependency trees returned by Maven artifact resolver (the default)</li>
     * <li>graph - record all direct dependencies of each artifact</li>
     */
    @Parameter(required = false, property = "manifestDependencies", defaultValue = "graph")
    String manifestDependencies;

    /**
     * @deprecated in favor of {@link #manifestDependencies}
     */
    @Deprecated(forRemoval = true)
    @Parameter(required = false, property = "flatManifest")
    boolean flatManifest;

    @Parameter(required = false, property = "redhatSupported")
    boolean redhatSupported;

    /**
     * Whether to calculate hashes for manifested components
     */
    @Parameter(property = "calculateHashes", defaultValue = "true")
    boolean calculateHashes;

    @Parameter(required = false)
    SbomConfig.ProductConfig productInfo;

    @Component
    QuarkusWorkspaceProvider bootstrapProvider;

    private Set<ArtifactCoords> targetBomConstraints;

    private MavenArtifactResolver resolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

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

        resolver = bootstrapProvider.createArtifactResolver(
                BootstrapMavenContext.config()
                        .setWorkspaceDiscovery(projectFile != null)
                        .setPreferPomsFromWorkspace(projectFile != null)
                        .setCurrentProject(projectFile == null ? null : projectFile.toString()));

        final List<Dependency> targetBomManagedDeps = getBomConstraints(targetBomCoords);
        targetBomConstraints = toCoordsSet(targetBomManagedDeps);

        final ExtensionCatalog catalog = resolveCatalog(catalogCoords);
        Collection<ArtifactCoords> rootArtifacts = getExtensionArtifacts(catalog);
        final Collection<ArtifactCoords> otherDescriptorCoords = getOtherMemberDescriptorCoords(catalog);
        List<ArtifactCoords> nonProjectBoms = List.of();
        if (!otherDescriptorCoords.isEmpty()) {
            if (targetBomCoords.getArtifactId().equals("quarkus-bom")) {
                if (!isManifestMode()) {
                    final Set<ArtifactCoords> collectedTargetDeps = new HashSet<>(rootArtifacts);
                    for (ArtifactCoords descrCoords : otherDescriptorCoords) {
                        final ExtensionCatalog otherCatalog = resolveCatalog(descrCoords);
                        var extensions = getExtensionArtifacts(otherCatalog);
                        if (!extensions.isEmpty()) {
                            final ArtifactCoords otherBomCoords = otherCatalog.getBom();
                            final List<Dependency> otherBomDeps = getBomConstraints(otherBomCoords);
                            final Set<ArtifactCoords> otherBomConstraints = toCoordsSet(otherBomDeps);
                            var managedDeps = new ArrayList<Dependency>(targetBomManagedDeps.size() + otherBomDeps.size());
                            managedDeps.addAll(targetBomManagedDeps);
                            managedDeps.addAll(otherBomDeps);
                            for (var e : extensions) {
                                final DependencyNode node;
                                try {
                                    node = resolver.collectManagedDependencies(
                                            toAetherArtifact(e),
                                            List.of(),
                                            managedDeps,
                                            List.of(), List.of()).getRoot();
                                } catch (BootstrapMavenException ex) {
                                    throw new RuntimeException(ex);
                                }
                                collectManagedByTargetBom(node, otherBomConstraints, collectedTargetDeps, e);
                            }
                        }
                    }
                    rootArtifacts = collectedTargetDeps;
                }
            } else {
                ArtifactCoords generatedCoreBomCoords = null;
                final String coreDescriptorArtifactId = PlatformArtifacts.ensureCatalogArtifactId("quarkus-bom");
                for (ArtifactCoords c : otherDescriptorCoords) {
                    if (c.getArtifactId().equals(coreDescriptorArtifactId)) {
                        generatedCoreBomCoords = PlatformArtifacts.ensureBomArtifact(c);
                        break;
                    }
                }
                if (generatedCoreBomCoords == null) {
                    throw new MojoExecutionException("Failed to locate quarkus-bom among " + otherDescriptorCoords);
                }
                nonProjectBoms = List.of(generatedCoreBomCoords);
            }
        }

        var depsConfigBuilder = ProjectDependencyConfig.builder()
                .setProductInfo(SbomConfig.ProductConfig.toProductInfo(productInfo))
                .setProjectBom(targetBomCoords)
                .setNonProjectBoms(nonProjectBoms)
                .setProjectArtifacts(rootArtifacts)
                .setIncludeGroupIds(dependenciesToBuild.getIncludeGroupIds())
                .setIncludeKeys(dependenciesToBuild.getIncludeKeys())
                .setIncludeArtifacts(dependenciesToBuild.getIncludeArtifacts())
                .setExcludePatterns(dependenciesToBuild.getExcludeArtifacts())
                .setExcludeGroupIds(dependenciesToBuild.getExcludeGroupIds())
                .setExcludeKeys(dependenciesToBuild.getExcludeKeys())
                .setExcludeBomImports(excludeBomImports)
                .setExcludeParentPoms(excludeParentPoms)
                .setLevel(level)
                .setLogArtifactsToBuild(logArtifactsToBuild)
                .setLogCodeRepoTree(logCodeRepoGraph)
                .setLogCodeRepos(logCodeRepos)
                .setLogModulesToBuild(logModulesToBuild)
                .setLogNonManagedVisited(logNonManagedVisited)
                .setLogRemaining(logRemaining)
                .setLogSummary(logSummary)
                .setLogTrees(logTrees)
                .setLogTreesFor(logTreesFor)
                .setIncludeAlreadyBuilt(includeAlreadyBuilt)
                .setLegacyScmLocator(legacyScmLocator)
                .setRecipeRepos(recipeRepos)
                .setWarnOnMissingScm(warnOnMissingScm);
        if (includeNonManaged != null) {
            depsConfigBuilder.setIncludeNonManaged(includeNonManaged);
        }
        if (manifest) {
            if ("none".equals(manifestDependencies)) {
                flatManifest = true;
            } else if (manifestDependencies.equals("tree")) {
                depsConfigBuilder.setVerboseGraphs(false);
            } else if (!manifestDependencies.equals("graph")) {
                throw new MojoExecutionException("Unrecognized value '" + manifestDependencies
                        + "' for parameter manifestDependencies. Supported values include graph, tree, none");
            } else {
                depsConfigBuilder.setVerboseGraphs(true);
            }
        }
        final ProjectDependencyConfig dependencyConfig = depsConfigBuilder.build();
        final ProjectDependencyResolver.Builder depsResolver = ProjectDependencyResolver.builder()
                .setArtifactResolver(resolver)
                .setMessageWriter(new MojoMessageWriter(getLog()))
                .setLogOutputFile(isManifestMode() ? null : (outputFile == null ? null : outputFile.toPath()))
                .setAppendOutput(appendOutput)
                .setDependencyConfig(dependencyConfig);

        if (manifest || flatManifest) {
            var sbomGenerator = new SbomGeneratingDependencyVisitor(
                    SbomGenerator.builder()
                            .setArtifactResolver(resolver)
                            .setOutputFile(outputFile == null ? null : outputFile.toPath())
                            .setEnableTransformers(false)
                            .setRecordDependencies(!flatManifest)
                            .setProductInfo(dependencyConfig.getProductInfo())
                            .setSchemaVersion(cdxSchemaVersion)
                            .setCalculateHashes(calculateHashes),
                    dependencyConfig);
            depsResolver.addDependencyTreeVisitor(sbomGenerator).build().resolveDependencies();
        } else {
            depsResolver.build().log();
        }
    }

    private Set<ArtifactCoords> toCoordsSet(List<Dependency> targetBomManagedDeps) {
        var targetBomConstraints = new HashSet<ArtifactCoords>(targetBomManagedDeps.size());
        for (Dependency d : targetBomManagedDeps) {
            targetBomConstraints.add(toCoords(d.getArtifact()));
        }
        return targetBomConstraints;
    }

    private void collectManagedByTargetBom(DependencyNode node,
            Set<ArtifactCoords> otherBomConstraints,
            Set<ArtifactCoords> collected,
            ArtifactCoords extension) {
        var coords = toCoords(node.getArtifact());
        if (isExcluded(coords)) {
            return;
        }
        if (targetBomConstraints.contains(coords)) {
            if (!RhVersionPattern.isRhVersion(coords.getVersion())) {
                if (collected.add(coords)) {
                    getLog().info(
                            String.format("Collected %s as a dependency of %s managed by the quarkus-bom",
                                    coords, extension));
                }
            }
        } else if (!Boolean.TRUE.equals(includeNonManaged) && !otherBomConstraints.contains(coords)) {
            return;
        }
        for (DependencyNode c : node.getChildren()) {
            collectManagedByTargetBom(c, otherBomConstraints, collected, extension);
        }
    }

    private Collection<ArtifactCoords> getExtensionArtifacts(ExtensionCatalog catalog) throws MojoExecutionException {
        final List<ArtifactCoords> supported = new ArrayList<>();
        for (Extension ext : catalog.getExtensions()) {
            ArtifactCoords rtArtifact = ext.getArtifact();
            if (isExcluded(rtArtifact)
                    || redhatSupported && !ext.getMetadata().containsKey(("redhat-support"))) {
                continue;
            }
            supported.add(ext.getArtifact());

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
            supported.add(deploymentCoords);
        }
        return supported;
    }

    private boolean isManifestMode() {
        return manifest || flatManifest;
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

    private static DefaultArtifact toAetherArtifact(ArtifactCoords a) {
        return new DefaultArtifact(a.getGroupId(),
                a.getArtifactId(), a.getClassifier(),
                a.getType(), a.getVersion());
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

    private boolean isExcluded(ArtifactCoords coords) {
        return dependenciesToBuild != null
                && (dependenciesToBuild.getExcludeGroupIds().contains(coords.getGroupId())
                        || dependenciesToBuild.getExcludeKeys().contains(coords.getKey())
                        || dependenciesToBuild.getExcludeArtifacts().contains(coords));
    }

    private static ArtifactCoords toCoords(Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private static Collection<ArtifactCoords> getOtherMemberDescriptorCoords(ExtensionCatalog catalog) {
        Map<String, Object> map = catalog.getMetadata();
        if (map == null) {
            return List.of();
        }
        Object o = map.get("platform-release");
        if (!(o instanceof Map)) {
            return List.of();
        }
        o = ((Map<?, ?>) o).get("members");
        if (!(o instanceof List)) {
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
