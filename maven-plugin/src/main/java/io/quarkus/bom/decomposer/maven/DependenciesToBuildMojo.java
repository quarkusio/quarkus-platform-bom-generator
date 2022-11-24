package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.maven.platformgen.PlatformConfig;
import io.quarkus.bom.platform.ProjectDependencyFilterConfig;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.paths.PathTree;
import io.quarkus.registry.CatalogMergeUtility;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.ExtensionOrigin;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

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

    @Parameter(required = false, property = "validateCodeRepoTags")
    boolean validateCodeRepoTags;

    /*
     * Whether to include dependencies that have already been built
     */
    @Parameter(property = "includeAlreadyBuilt", required = false)
    boolean includeAlreadyBuilt;

    private Set<ArtifactCoords> targetBomConstraints;
    private Map<ArtifactCoords, List<Dependency>> enforcedConstraintsForBom = new HashMap<>();

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

        final List<Dependency> targetBomManagedDeps = getBomConstraints(targetBomCoords);
        targetBomConstraints = new HashSet<>(targetBomManagedDeps.size());
        for (Dependency d : targetBomManagedDeps) {
            targetBomConstraints.add(toCoords(d.getArtifact()));
        }

        ExtensionCatalog catalog = resolveCatalog(catalogCoords);

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

        final Map<ArtifactCoords, Extension> supported = new HashMap<>();
        for (Extension ext : catalog.getExtensions()) {
            ArtifactCoords rtArtifact = ext.getArtifact();
            if (isExcluded(rtArtifact)) {
                continue;
            }
            Object o = ext.getMetadata().get("redhat-support");
            if (o == null) {
                continue;
            }
            supported.put(ext.getArtifact(), ext);

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
            supported.put(deploymentCoords, ext);
        }

        ProjectDependencyResolver.builder()
                .setArtifactConstraintsProvider(coords -> {
                    final Extension ext = supported.get(coords);
                    return ext == null ? targetBomManagedDeps : getConstraintsForExtension(ext);
                })
                .setArtifactResolver(resolver)
                .setMessageWriter(new MojoMessageWriter(getLog()))
                .setLogOutputFile(outputFile == null ? null : outputFile.toPath())
                .setAppendOutput(appendOutput)
                .setDependencyConfig(ProjectDependencyConfig.builder()
                        .setProjectBom(targetBomCoords)
                        .setProjectArtifacts(supported.keySet())
                        .setIncludeGroupIds(dependenciesToBuild.getIncludeGroupIds())
                        .setIncludeKeys(dependenciesToBuild.getIncludeKeys())
                        .setIncludeArtifacts(dependenciesToBuild.getIncludeArtifacts())
                        .setExcludePatterns(dependenciesToBuild.getExcludeArtifacts())
                        .setExcludeGroupIds(dependenciesToBuild.getExcludeGroupIds())
                        .setExcludeKeys(dependenciesToBuild.getExcludeKeys())
                        .setExcludeBomImports(excludeBomImports)
                        .setExcludeParentPoms(excludeParentPoms)
                        .setIncludeNonManaged(includeNonManaged)
                        .setLevel(level)
                        .setLogArtifactsToBuild(logArtifactsToBuild)
                        .setLogCodeRepoTree(logCodeRepoGraph)
                        .setLogCodeRepos(logCodeRepos)
                        .setLogModulesToBuild(logModulesToBuild)
                        .setLogNonManagedVisited(logNonManagedVisited)
                        .setLogRemaining(logRemaining)
                        .setLogSummary(logSummary)
                        .setLogTrees(logTrees)
                        .setIncludeAlreadyBuilt(includeAlreadyBuilt)
                        .setValidateCodeRepoTags(validateCodeRepoTags))
                .build().log();
    }

    private List<Dependency> getConstraintsForExtension(Extension ext) {
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
        throw new RuntimeException(sb.toString());
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
