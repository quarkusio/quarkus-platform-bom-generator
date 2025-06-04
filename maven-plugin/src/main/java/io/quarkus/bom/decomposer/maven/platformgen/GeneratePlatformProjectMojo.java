package io.quarkus.bom.decomposer.maven.platformgen;

import static io.quarkus.bom.decomposer.maven.util.Utils.newModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.maven.GenerateMavenRepoZip;
import io.quarkus.bom.decomposer.maven.MojoMessageWriter;
import io.quarkus.bom.decomposer.maven.QuarkusWorkspaceProvider;
import io.quarkus.bom.decomposer.maven.util.Utils;
import io.quarkus.bom.diff.BomDiff;
import io.quarkus.bom.diff.HtmlBomDiffReportGenerator;
import io.quarkus.bom.platform.ForeignPreferredConstraint;
import io.quarkus.bom.platform.PlatformBomComposer;
import io.quarkus.bom.platform.PlatformBomConfig;
import io.quarkus.bom.platform.PlatformBomUtils;
import io.quarkus.bom.platform.PlatformCatalogResolver;
import io.quarkus.bom.platform.PlatformMember;
import io.quarkus.bom.platform.PlatformMemberConfig;
import io.quarkus.bom.platform.PlatformMemberTestConfig;
import io.quarkus.bom.platform.PlatformMemberTestConfig.Copy;
import io.quarkus.bom.platform.ProjectDependencyFilterConfig;
import io.quarkus.bom.platform.RedHatExtensionDependencyCheck;
import io.quarkus.bom.platform.ReportIndexPageGenerator;
import io.quarkus.bom.platform.SbomConfig;
import io.quarkus.bom.platform.SbomerConfig;
import io.quarkus.bom.platform.SbomerGeneratorConfig;
import io.quarkus.bom.platform.SbomerProcessorConfig;
import io.quarkus.bom.platform.SbomerProductConfig;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bom.resolver.EffectiveModelResolver;
import io.quarkus.bom.task.PlatformGenTaskScheduler;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.domino.ArtifactCoordsPattern;
import io.quarkus.domino.DominoInfo;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.paths.PathTree;
import io.quarkus.registry.Constants;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import nu.studer.java.util.OrderedProperties;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;

@Mojo(name = "generate-platform-project", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE)
public class GeneratePlatformProjectMojo extends AbstractMojo {

    private static final String POM_XML = "pom.xml";
    private static final String LAST_BOM_UPDATE = "last-bom-update";
    private static final String MEMBER_LAST_BOM_UPDATE_PROP = "member.last-bom-update";
    private static final String PLATFORM_KEY_PROP = "platform.key";
    private static final String PLATFORM_STREAM_PROP = "platform.stream";
    private static final String PLATFORM_RELEASE_PROP = "platform.release";
    private static final String DEPENDENCIES_TO_BUILD = "dependenciesToBuild";
    private static final String COLON = ":";
    public static final String TEST = "test";
    public static final String ARG_LINE = "argLine";
    public static final String ENVIRONMENT_VARIABLES = "environmentVariables";
    public static final String SYSTEM_PROPERTY_VARIABLES = "systemPropertyVariables";
    public static final String CONFIGURATION = "configuration";
    public static final String INCLUDES = "includes";
    public static final String EXCLUDES = "excludes";
    public static final String EXCLUDE = "exclude";
    public static final String INCLUDE = "include";

    @Component
    RepositorySystem repoSystem;

    @Component
    QuarkusWorkspaceProvider mvnProvider;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(defaultValue = "${project}")
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    MavenSession session;

    @Parameter(required = true, defaultValue = "${basedir}/generated-platform-project")
    File outputDir;

    @Parameter(required = true, defaultValue = "${project.build.directory}/reports")
    File reportsDir;

    @Parameter(required = true, defaultValue = "${project.build.directory}")
    File buildDir;

    @Parameter(required = true)
    PlatformConfig platformConfig;
    private PlatformReleaseConfig platformReleaseConfig;

    @Parameter(required = false)
    ProjectDependencyFilterConfig dependenciesToBuild;

    @Parameter(required = true, defaultValue = "${project.build.directory}/updated-pom.xml")
    File updatedPom;

    @Parameter(property = "recordUpdatedBoms")
    boolean recordUpdatedBoms;

    Artifact universalBom;
    MavenArtifactResolver nonWsResolver;
    MavenArtifactResolver wsAwareResolver;

    PlatformCatalogResolver catalogs;
    final Map<ArtifactKey, PlatformMemberImpl> members = new LinkedHashMap<>();

    private PlatformMemberImpl quarkusCore;

    private DecomposedBom universalGeneratedBom;

    private Path universalPlatformBomXml;

    private PluginDescriptor pluginDescr;

    private List<String> pomLines;

    private final Map<ArtifactKey, String> universalBomDepKeys = new HashMap<>();

    private TransformerFactory transformerFactory;

    // POM property names by values
    private final Map<String, String> pomPropsByValues = new HashMap<>();

    private List<Profile> generatedBomReleaseProfile;

    private boolean isClean() {
        final List<String> goals;
        if (session.getGoals().isEmpty()) {
            if (project.getDefaultGoal() == null) {
                return false;
            }
            goals = Arrays.asList(project.getDefaultGoal().split("\\s+"));
        } else {
            goals = session.getGoals();
        }
        return goals.contains("clean");
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (isClean()) {
            getLog().info("Deleting " + outputDir);
            IoUtils.recursiveDelete(outputDir.toPath());
        }

        quarkusCore = new PlatformMemberImpl(platformConfig.getCore());
        members.put(quarkusCore.key(), quarkusCore);
        for (PlatformMemberConfig memberConfig : platformConfig.getMembers()) {
            if (memberConfig.isEnabled()) {
                final PlatformMemberImpl member = new PlatformMemberImpl(memberConfig);
                members.put(member.key(), member);
            }
        }

        final Model pom = newModel();
        final String rootArtifactIdBase = getArtifactIdBase(project.getModel());
        pom.setArtifactId(rootArtifactIdBase + "-parent");
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(artifactIdToName(rootArtifactIdBase) + " - Parent");

        final File pomXml = new File(outputDir, POM_XML);
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(project.getGroupId());
        parent.setArtifactId(project.getArtifactId());
        parent.setRelativePath(pomXml.toPath().getParent().relativize(project.getFile().getParentFile().toPath()).toString());
        pom.setParent(parent);
        setParentVersion(pom, project.getOriginalModel());
        if (parent.getVersion().equals("${revision}")) {
            pom.addProperty("revision", project.getVersion());
        }

        final PluginManagement pm = new PluginManagement();
        getOrCreateBuild(pom).setPluginManagement(pm);
        final Plugin plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        plugin.setVersion(getTestArtifactVersion(pluginDescriptor().getGroupId(), pluginDescriptor().getVersion()));
        plugin.setExtensions(true);
        // to be able to initialize the resolver
        persistPom(pom);

        generateUniversalPlatformModule(pom);
        try {
            generateMemberModules(pom);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate platform project", e);
        }

        if (dependenciesToBuild != null) {
            generateDepsToBuildModule(pom);
            generateSbomModule(pom);
        }

        if (platformConfig.getGenerateMavenRepoZip() != null) {
            generateMavenRepoZipModule(pom);
        }

        generateExtensionChangesModule(pom);

        // keep the Maven plugin as the last module to make sure the last module is deployable by the Nexus plugin
        if (platformConfig.getAttachedMavenPlugin() != null) {
            generateMavenPluginModule(pom);
        }

        addReleaseProfile(pom);

        pom.getProperties().setProperty(PLATFORM_KEY_PROP, releaseConfig().getPlatformKey());
        pom.getProperties().setProperty(PLATFORM_STREAM_PROP, releaseConfig().getStream());
        pom.getProperties().setProperty(PLATFORM_RELEASE_PROP, releaseConfig().getVersion());

        persistPom(pom);

        recordUpdatedBoms();
        generateDominoCliConfig();
    }

    private void generateMemberModules(Model parentPom) throws Exception {
        final PlatformGenTaskScheduler scheduler = PlatformGenTaskScheduler.getInstance();
        for (PlatformMemberImpl member : members.values()) {
            final String moduleName = getArtifactIdBase(member.getGeneratedPlatformBom().getArtifactId());
            parentPom.addModule(moduleName);
            scheduler.schedule(() -> generateMemberModule(parentPom, member, moduleName, scheduler));
        }
        scheduler.schedule(() -> generateBomReports(scheduler));
        scheduler.waitForCompletion();
        if (scheduler.hasErrors()) {
            for (var e : scheduler.getErrors()) {
                getLog().error(e);
            }
            throw new MojoExecutionException("Failed to generate platform project, please see the errors logged above");
        }
    }

    private void generateMemberModule(Model parentPom, PlatformMemberImpl member, String moduleName,
            PlatformGenTaskScheduler scheduler) throws Exception {
        generateMemberModule(member, moduleName, parentPom);
        generateMemberBom(member);
        if (member.config().hasTests()) {
            generateMemberIntegrationTestsModule(member, scheduler);
        }
        scheduler.schedule(() -> {
            generatePlatformDescriptorModule(member.descriptorCoords(), member.baseModel,
                    quarkusCore.getInputBom().equals(member.getInputBom()),
                    platformConfig.getAttachedMavenPlugin(), member);
            generatePlatformPropertiesModule(member, true);
        });
        scheduler.addFinializingTask(() -> persistPom(member.baseModel));
    }

    private static void setParentVersion(Model model, Model parentModel) {
        var parent = model.getParent();
        var version = parentModel.getVersion();
        if (version == null) {
            var pp = parentModel.getParent();
            if (pp == null) {
                throw new IllegalArgumentException("Failed to determine the version for the parent model");
            }
            version = pp.getVersion();
        }
        parent.setVersion(version);
    }

    private void generateBomReports(PlatformGenTaskScheduler scheduler) throws Exception {
        if (!(platformConfig.isGenerateBomReports() || platformConfig.getGenerateBomReportsZip() != null)) {
            return;
        }
        final Path reportsOutputDir = reportsDir.toPath();
        // reset the resolver to pick up all the generated platform modules
        //resetResolver();
        final ReportIndexPageGenerator index;
        try {
            index = new ReportIndexPageGenerator(reportsOutputDir.resolve("index.html"));
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }

        final Path releasesReport = reportsOutputDir.resolve("main").resolve("generated-releases.html");
        generateReleasesReport(universalGeneratedBom, releasesReport);
        try {
            index.universalBom(universalPlatformBomXml.toUri().toURL(), universalGeneratedBom, releasesReport);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException(e);
        }

        var artifactResolver = ArtifactResolverProvider.get(getWorkspaceAwareMavenResolver());
        for (PlatformMemberImpl member : members.values()) {
            if (member.getInputBom() != null) {
                scheduler.schedule(() -> generateBomReports(member.originalBom, member.generatedBom,
                        reportsOutputDir.resolve(member.config().getName().toLowerCase()), index,
                        member.generatedPomFile, artifactResolver));
            }
        }
        scheduler.addFinializingTask(index::close);

        if (platformConfig.getGenerateBomReportsZip() != null) {
            scheduler.addFinializingTask(() -> {
                Path zip = Paths.get(platformConfig.getGenerateBomReportsZip());
                if (!zip.isAbsolute()) {
                    zip = reportsOutputDir.getParent().resolve(zip);
                }
                try {
                    Files.createDirectories(zip.getParent());
                    ZipUtils.zip(reportsOutputDir, zip);
                } catch (Exception e) {
                    throw new MojoExecutionException("Failed to ZIP platform member BOM reports", e);
                }
            });
        }
    }

    private void generateDominoCliConfig() throws MojoExecutionException {
        if (!Boolean.parseBoolean(project.getProperties().getProperty("generate-domino-cli-config"))) {
            return;
        }
        generateDominoManifestCliConfig();
        generateDominoBuildCliConfig();
    }

    private void generateDominoManifestCliConfig() throws MojoExecutionException {
        final Path dominoDir = deleteAndCreateDir(Path.of(DominoInfo.CONFIG_DIR_NAME).resolve("manifest"));
        final SbomerGlobalConfig globalSbomerConfig = platformConfig.getSbomer();
        SbomerConfig sbomerConfig = null;
        for (PlatformMember member : members.values()) {
            if (!member.config().isEnabled() || member.config().isHidden()) {
                continue;
            }
            getLog().info(
                    "Generating Domino CLI SBOM generator config for " + member.getGeneratedPlatformBom().getArtifactId());
            final Path dominoManifestConfigFile = dominoDir
                    .resolve(member.getGeneratedPlatformBom().getArtifactId() + "-config.json");

            final ProjectDependencyConfig.Mutable dominoConfig = ProjectDependencyConfig.builder()
                    .setProjectBom(PlatformArtifacts.ensureBomArtifact(member.descriptorCoords()))
                    .setProductInfo(SbomConfig.ProductConfig.toProductInfo(getProductInfo(member)));

            if (!this.quarkusCore.descriptorCoords().equals(member.descriptorCoords())) {
                var quarkusBom = quarkusCore.getGeneratedPlatformBom();
                dominoConfig.setNonProjectBoms(List.of(ArtifactCoords.pom(quarkusBom.getGroupId(),
                        quarkusBom.getArtifactId(), quarkusBom.getVersion())));
            }

            var sbomConfig = member.config().getSbom();
            if (sbomConfig != null && sbomConfig.getErrata() != null) {
                if (sbomerConfig == null) {
                    sbomerConfig = new SbomerConfig();
                    if (globalSbomerConfig != null) {
                        sbomerConfig.setApiVersion(globalSbomerConfig.getApiVersion());
                        sbomerConfig.setType(globalSbomerConfig.getType());
                    }
                }
                final SbomerProductConfig product = new SbomerProductConfig();
                final SbomerProcessorConfig processor = new SbomerProcessorConfig();
                processor.setErrata(sbomConfig.getErrata());
                final SbomerGeneratorConfig generator = new SbomerGeneratorConfig();
                generator.setArgs("--config-file " + dominoManifestConfigFile);
                if (globalSbomerConfig != null) {
                    processor.setType(globalSbomerConfig.getProcessorType());
                    generator.setType(globalSbomerConfig.getGeneratorType());
                    generator.setVersion(globalSbomerConfig.getGeneratorVersion());
                    if (globalSbomerConfig.getArgs() != null) {
                        generator.setArgs(generator.getArgs() + " " + globalSbomerConfig.getArgs());
                    }
                }
                product.setProcessors(List.of(processor));
                product.setGenerator(generator);
                sbomerConfig.addProduct(product);
            }

            List<ArtifactCoordsPattern> excludePatterns = List.of();
            if (sbomConfig == null || sbomConfig.isApplyDependenciesToBuildInclusions()) {
                var depsToBuild = effectiveMemberDepsToBuildConfig(member.config());
                dominoConfig.setIncludeArtifacts(depsToBuild.getIncludeArtifacts())
                        .setIncludeGroupIds(depsToBuild.getIncludeGroupIds())
                        .setIncludeKeys(depsToBuild.getIncludeKeys());
            } else if (sbomConfig.isApplyCompleteDependenciesToBuildConfig()) {
                var depsToBuild = effectiveMemberDepsToBuildConfig(member.config());
                dominoConfig.setIncludeArtifacts(depsToBuild.getIncludeArtifacts())
                        .setIncludeGroupIds(depsToBuild.getIncludeGroupIds())
                        .setIncludeKeys(depsToBuild.getIncludeKeys())
                        .setExcludePatterns(depsToBuild.getExcludeArtifacts())
                        .setExcludeGroupIds(depsToBuild.getExcludeGroupIds())
                        .setExcludeKeys(depsToBuild.getExcludeKeys());
                excludePatterns = ArtifactCoordsPattern.toPatterns(dominoConfig.getExcludePatterns());
            }

            Set<ArtifactKey> selectedKeys = Set.of();
            if (sbomConfig != null && sbomConfig.isSupportedExtensionsOnly()) {
                List<Path> metadataOverrides = new ArrayList<>();
                for (String s : member.config().getMetadataOverrideFiles()) {
                    metadataOverrides.add(Path.of(s));
                }
                for (String s : member.config().getMetadataOverrideArtifacts()) {
                    try {
                        metadataOverrides
                                .add(getNonWorkspaceResolver().resolve(toAetherArtifact(s)).getArtifact().getFile().toPath());
                    } catch (BootstrapMavenException e) {
                        throw new MojoExecutionException("Failed to resolve " + s, e);
                    }
                }
                if (metadataOverrides.isEmpty()) {
                    throw new IllegalStateException("The SBOM generator for member " + member.config().getName()
                            + " is configured to include only supported extensions but no support metadata override sources were provided");
                }
                selectedKeys = new HashSet<>(metadataOverrides.size());
                for (Path p : metadataOverrides) {
                    try {
                        ExtensionCatalog c = ExtensionCatalog.fromFile(p);
                        for (var e : c.getExtensions()) {
                            if (e.getMetadata().containsKey("redhat-support")) {
                                var key = e.getArtifact().getKey();
                                selectedKeys.add(key);
                                selectedKeys.add(ArtifactKey.of(key.getGroupId(), key.getArtifactId() + "-deployment",
                                        key.getClassifier(), key.getType()));
                            }
                        }
                    } catch (IOException e) {
                        throw new MojoExecutionException("Failed to deserialize " + p, e);
                    }
                }
            }
            for (ProjectRelease r : member.getAlignedDecomposedBom().releases()) {
                for (ProjectDependency d : r.dependencies()) {
                    var a = d.artifact();
                    if ((selectedKeys.isEmpty() || selectedKeys.contains(d.key()))
                            && isExtensionCandidate(a, member.config().getExtensionGroupIds(), excludePatterns)) {
                        addExtensionArtifacts(a, dominoConfig);
                    }
                }
            }

            try {
                dominoConfig.build().persist(dominoManifestConfigFile.normalize().toAbsolutePath());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to persist Domino config", e);
            }
        }

        if (sbomerConfig != null) {
            sbomerConfig.serialize(deleteAndCreateDir(Path.of(".sbomer")).resolve("config.yaml"));
        }
    }

    private void generateDominoBuildCliConfig() throws MojoExecutionException {
        final Path dominoDir = deleteAndCreateDir(
                Path.of(DominoInfo.CONFIG_DIR_NAME).normalize().toAbsolutePath().resolve("build"));
        for (PlatformMember member : members.values()) {
            if (!member.config().isEnabled() || member.config().isHidden()) {
                continue;
            }
            getLog().info("Generating Domino CLI build config for " + member.getGeneratedPlatformBom().getArtifactId());

            final ProjectDependencyConfig.Mutable dominoConfig = ProjectDependencyConfig.builder();
            if (member.getInputBom() != null) {
                dominoConfig.setProjectBom(ArtifactCoords.pom(member.getInputBom().getGroupId(),
                        member.getInputBom().getArtifactId(), member.getInputBom().getVersion()));
            }

            if (!this.quarkusCore.descriptorCoords().equals(member.descriptorCoords())) {
                var quarkusBom = quarkusCore.getGeneratedPlatformBom();
                dominoConfig.setNonProjectBoms(List.of(ArtifactCoords.pom(quarkusBom.getGroupId(),
                        quarkusBom.getArtifactId(), quarkusBom.getVersion())));
            }

            var depsToBuild = effectiveMemberDepsToBuildConfig(member.config());
            dominoConfig.setIncludeArtifacts(depsToBuild.getIncludeArtifacts())
                    .setIncludeGroupIds(depsToBuild.getIncludeGroupIds())
                    .setIncludeKeys(depsToBuild.getIncludeKeys())
                    .setExcludePatterns(depsToBuild.getExcludeArtifacts())
                    .setExcludeGroupIds(depsToBuild.getExcludeGroupIds())
                    .setExcludeKeys(depsToBuild.getExcludeKeys());
            final List<ArtifactCoordsPattern> excludePatterns = ArtifactCoordsPattern
                    .toPatterns(dominoConfig.getExcludePatterns());

            List<Path> metadataOverrides = new ArrayList<>();
            for (String s : member.config().getMetadataOverrideFiles()) {
                metadataOverrides.add(Path.of(s));
            }
            for (String s : member.config().getMetadataOverrideArtifacts()) {
                try {
                    metadataOverrides
                            .add(getNonWorkspaceResolver().resolve(toAetherArtifact(s)).getArtifact().getFile().toPath());
                } catch (BootstrapMavenException e) {
                    throw new MojoExecutionException("Failed to resolve " + s, e);
                }
            }
            if (metadataOverrides.isEmpty()) {
                throw new IllegalStateException("The SBOM generator for member " + member.config().getName()
                        + " is configured to include only supported extensions but no support metadata override sources were provided");
            }
            final Set<ArtifactKey> selectedKeys = new HashSet<>(metadataOverrides.size());
            for (Path p : metadataOverrides) {
                try {
                    ExtensionCatalog c = ExtensionCatalog.fromFile(p);
                    for (var e : c.getExtensions()) {
                        if (e.getMetadata().containsKey("redhat-support")) {
                            var key = e.getArtifact().getKey();
                            selectedKeys.add(key);
                            selectedKeys.add(ArtifactKey.of(key.getGroupId(), key.getArtifactId() + "-deployment",
                                    key.getClassifier(), key.getType()));
                        }
                    }
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to deserialize " + p, e);
                }
            }

            for (ProjectRelease r : member.getAlignedDecomposedBom().releases()) {
                for (ProjectDependency d : r.dependencies()) {
                    var a = d.artifact();
                    if ((selectedKeys.isEmpty() || selectedKeys.contains(d.key()))
                            && isExtensionCandidate(a, member.config().getExtensionGroupIds(), excludePatterns)) {
                        addExtensionArtifacts(a, dominoConfig);
                    }
                }
            }

            try {
                dominoConfig.build()
                        .persist(dominoDir.resolve(member.getGeneratedPlatformBom().getArtifactId() + "-config.json"));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to persist Domino config", e);
            }
        }
    }

    private void addExtensionArtifacts(Artifact a, ProjectDependencyConfig.Mutable dominoConfig) {
        final Artifact resolved;
        final boolean relocated;
        if (a.getFile() == null) {
            var resolver = getNonWorkspaceResolver();
            // this trick is done to capture relocations, i.e. when {@code a} was relocated to another artifact
            var request = new DependencyRequest()
                    .setCollectRequest(new CollectRequest()
                            .setRootArtifact(a)
                            .setDependencies(List.of(new org.eclipse.aether.graph.Dependency(a, JavaScopes.COMPILE, false,
                                    List.of(new org.eclipse.aether.graph.Exclusion("*", "*", "*", "*")))))
                            .setRepositories(resolver.getRepositories()));
            List<DependencyNode> resolvedDeps;
            try {
                resolvedDeps = resolver.getSystem().resolveDependencies(resolver.getSession(), request).getRoot().getChildren();
            } catch (DependencyResolutionException e) {
                throw new RuntimeException("Failed to resolve " + a, e);
            }
            if (resolvedDeps.size() != 1) {
                throw new IllegalStateException("Expected a single dependency but got " + resolvedDeps);
            }
            var node = resolvedDeps.get(0);
            resolved = node.getArtifact();
            relocated = !node.getRelocations().isEmpty();
        } else {
            resolved = a;
            relocated = false;
        }
        PathTree.ofArchive(resolved.getFile().toPath()).accept(BootstrapConstants.DESCRIPTOR_PATH, visit -> {
            if (visit != null) {
                var props = new Properties();
                try (BufferedReader reader = Files.newBufferedReader(visit.getPath())) {
                    props.load(reader);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (relocated) {
                    dominoConfig.addProjectArtifacts(ArtifactCoords.of(a.getGroupId(),
                            a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion()));
                }
                dominoConfig.addProjectArtifacts(ArtifactCoords.of(resolved.getGroupId(),
                        resolved.getArtifactId(), resolved.getClassifier(),
                        resolved.getExtension(), resolved.getVersion()));
                var deploymentArtifact = props.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
                if (deploymentArtifact == null) {
                    getLog().warn("Failed to identify the deployment artifact for " + resolved + " in "
                            + visit.getUrl());
                } else {
                    dominoConfig.addProjectArtifacts(ArtifactCoords.fromString(deploymentArtifact));
                }
            }
        });
    }

    private static boolean isExtensionCandidate(Artifact a, Collection<String> extensionGroupIds,
            Collection<ArtifactCoordsPattern> excludePatterns) {
        if (!a.getExtension().equals(ArtifactCoords.TYPE_JAR)
                || "javadoc".equals(a.getClassifier())
                || "tests".equals(a.getClassifier())
                || "sources".equals(a.getClassifier())
                || a.getArtifactId().endsWith("-deployment")
                || !extensionGroupIds.isEmpty() && !extensionGroupIds.contains(a.getGroupId())) {
            return false;
        }
        for (ArtifactCoordsPattern pattern : excludePatterns) {
            if (pattern.matches(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion())) {
                return false;
            }
        }
        return true;
    }

    private void generateExtensionChangesModule(Model parentPom) throws MojoExecutionException {
        final String artifactId = "quarkus-extension-changes";
        final Model pom = newModel();
        pom.setArtifactId(artifactId);
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(artifactId));
        parentPom.addModule(artifactId);
        final File pomXml = getPomFile(parentPom, artifactId);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);
        Utils.skipInstallAndDeploy(pom);

        Plugin plugin = new Plugin();
        Build build = getOrCreateBuild(pom);
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        final String profileId = "extensionChanges";
        Profile profile = new Profile();
        profile.setId(profileId);
        pom.addProfile(profile);
        final Activation activation = new Activation();
        profile.setActivation(activation);
        final ActivationProperty ap = new ActivationProperty();
        activation.setProperty(ap);
        ap.setName(profileId);

        build = getOrCreateBuild(profile);

        PluginManagement pm = new PluginManagement();
        build.setPluginManagement(pm);
        plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        final StringBuilder sb = new StringBuilder();
        for (PlatformMemberImpl m : members.values()) {
            if (!m.config.isHidden() && m.config.isEnabled()) {
                sb.append("quarkus-platform-bom:extension-changes@").append(m.getConfiguredPlatformBom().getArtifactId())
                        .append(' ');
            }
        }
        build.setDefaultGoal(sb.toString());

        Path outputDir = buildDir.toPath().resolve("extension-changes");
        final String prefix = pomXml.toPath().getParent().relativize(outputDir).toString();
        for (PlatformMemberImpl m : members.values()) {
            if (m.config.isHidden() || !m.config.isEnabled()) {
                continue;
            }
            final PluginExecution exec = new PluginExecution();
            plugin.addExecution(exec);
            exec.setId(m.getConfiguredPlatformBom().getArtifactId());
            exec.setPhase("process-resources");
            exec.addGoal("extension-changes");
            final Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
            exec.setConfiguration(config);
            config.addChild(textDomElement("bom",
                    m.getConfiguredPlatformBom().getGroupId() + COLON + m.getConfiguredPlatformBom().getArtifactId() + COLON
                            + getDependencyVersion(pom, m.descriptorCoords())));
            config.addChild(
                    textDomElement("outputFile",
                            prefix + "/" + m.getConfiguredPlatformBom().getArtifactId() + "-extension-changes.json"));
        }

        persistPom(pom);
    }

    private void generateDepsToBuildModule(Model parentPom) throws MojoExecutionException {
        generateDepsToBuildModule(parentPom, "quarkus-dependencies-to-build", "depsToBuild", "dependencies-to-build",
                "-deps-to-build.txt", false);
    }

    private void generateSbomModule(Model parentPom) throws MojoExecutionException {
        generateDepsToBuildModule(parentPom, "quarkus-sbom", "sbom", "sbom", "-sbom.json", true);
    }

    private void generateDepsToBuildModule(Model parentPom, String artifactId, String profileId, String outputDirName,
            String outputFileSuffix, boolean forSbom) throws MojoExecutionException {
        final Model pom = newModel();
        pom.setArtifactId(artifactId);
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(artifactId));
        parentPom.addModule(artifactId);
        final File pomXml = getPomFile(parentPom, artifactId);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);
        Utils.skipInstallAndDeploy(pom);

        Plugin plugin = new Plugin();
        Build build = getOrCreateBuild(pom);
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        Profile profile = new Profile();
        profile.setId(profileId);
        pom.addProfile(profile);
        final Activation activation = new Activation();
        profile.setActivation(activation);
        final ActivationProperty ap = new ActivationProperty();
        activation.setProperty(ap);
        ap.setName(profileId);

        build = getOrCreateBuild(profile);

        PluginManagement pm = new PluginManagement();
        build.setPluginManagement(pm);
        plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        final StringBuilder sb = new StringBuilder();
        for (PlatformMemberImpl m : members.values()) {
            if (!m.config.isHidden() && m.config.isEnabled()) {
                sb.append("quarkus-platform-bom:dependencies-to-build@").append(m.getConfiguredPlatformBom().getArtifactId())
                        .append(' ');
            }
        }
        build.setDefaultGoal(sb.toString());

        Path outputDir = buildDir.toPath().resolve(outputDirName);
        final String prefix = pomXml.toPath().getParent().relativize(outputDir).toString();
        for (PlatformMemberImpl m : members.values()) {
            if (m.config.isHidden() || !m.config.isEnabled()) {
                continue;
            }
            final PluginExecution exec = new PluginExecution();
            plugin.addExecution(exec);
            exec.setId(m.getConfiguredPlatformBom().getArtifactId());
            exec.setPhase("process-resources");
            exec.addGoal("dependencies-to-build");
            final Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
            exec.setConfiguration(config);
            config.addChild(textDomElement("bom",
                    m.getConfiguredPlatformBom().getGroupId() + COLON + m.getConfiguredPlatformBom().getArtifactId() + COLON
                            + getDependencyVersion(pom, m.descriptorCoords())));
            config.addChild(
                    textDomElement("outputFile",
                            prefix + "/" + m.getConfiguredPlatformBom().getArtifactId() + outputFileSuffix));

            final SbomConfig sbomConfig = forSbom ? m.config().getSbom() : null;
            if (sbomConfig != null) {
                var productConfig = getProductInfo(m);
                if (productConfig != null) {
                    final Xpp3Dom productInfoDom = newDomSelfAppend("productInfo");
                    config.addChild(productInfoDom);
                    if (productConfig.getId() != null) {
                        productInfoDom.addChild(textDomElement("id", productConfig.getId()));
                    }
                    if (productConfig.getStream() != null) {
                        productInfoDom.addChild(textDomElement("stream", productConfig.getStream()));
                    }
                    productInfoDom.addChild(textDomElement("type", productConfig.getType().toUpperCase()));
                    productInfoDom.addChild(textDomElement("group", productConfig.getGroup()));
                    productInfoDom.addChild(textDomElement("name", productConfig.getName()));
                    productInfoDom.addChild(textDomElement("version", productConfig.getVersion()));
                    if (productConfig.getPurl() != null) {
                        productInfoDom.addChild(textDomElement("purl", productConfig.getPurl()));
                    }
                    if (productConfig.getCpe() != null) {
                        productInfoDom.addChild(textDomElement("cpe", productConfig.getCpe()));
                    }
                    if (productConfig.getDescription() != null) {
                        productInfoDom.addChild(textDomElement("description", productConfig.getDescription()));
                    }

                    var rn = productConfig.getReleaseNotes();
                    if (rn != null) {
                        final Xpp3Dom releaseNotesDom = newDomSelfAppend("releaseNotes");
                        productInfoDom.addChild(releaseNotesDom);
                        if (rn.getType() != null) {
                            releaseNotesDom.addChild(textDomElement("type", rn.getType()));
                        }
                        if (rn.getTitle() != null) {
                            releaseNotesDom.addChild(textDomElement("title", rn.getTitle()));
                        }
                        if (!rn.getAliases().isEmpty()) {
                            final Xpp3Dom aliasesDom = newDomSelfAppend("aliases");
                            releaseNotesDom.addChild(aliasesDom);
                            for (String a : rn.getAliases()) {
                                aliasesDom.addChild(textDomElement("alias", a));
                            }
                        }
                        if (!rn.getProperties().isEmpty()) {
                            final Xpp3Dom propsDom = newDomSelfAppend("properties");
                            releaseNotesDom.addChild(propsDom);
                            final List<String> names = new ArrayList<>(rn.getProperties().keySet());
                            Collections.sort(names);
                            for (String name : names) {
                                propsDom.addChild(textDomElement(name, rn.getProperties().get(name)));
                            }
                        }
                    }
                }

                if (sbomConfig.isSupportedExtensionsOnly()) {
                    config.addChild(textDomElement("redhatSupported", "true"));
                }
            }

            if (!forSbom || sbomConfig != null && sbomConfig.isApplyCompleteDependenciesToBuildConfig()) {
                final ProjectDependencyFilterConfig depsToBuildConfig = m.config().getDependenciesToBuild();
                if (depsToBuildConfig != null) {
                    final Xpp3Dom depsToBuildDom = newDomSelfAppend(DEPENDENCIES_TO_BUILD);
                    config.addChild(depsToBuildDom);
                    if (!depsToBuildConfig.getExcludeArtifacts().isEmpty()) {
                        final Xpp3Dom excludeArtifactsDom = newDomChildrenAppend("excludeArtifacts");
                        depsToBuildDom.addChild(excludeArtifactsDom);
                        for (String artifact : sortAsString(depsToBuildConfig.getExcludeArtifacts())) {
                            excludeArtifactsDom.addChild(textDomElement("artifact", artifact));
                        }
                    }
                    if (!depsToBuildConfig.getExcludeGroupIds().isEmpty()) {
                        final Xpp3Dom excludeGroupIdsDom = newDomChildrenAppend("excludeGroupIds");
                        depsToBuildDom.addChild(excludeGroupIdsDom);
                        for (String groupId : sortAsString(depsToBuildConfig.getExcludeGroupIds())) {
                            excludeGroupIdsDom.addChild(textDomElement("groupId", groupId));
                        }
                    }
                    if (!depsToBuildConfig.getExcludeKeys().isEmpty()) {
                        final Xpp3Dom excludeKeysDom = newDomChildrenAppend("excludeKeys");
                        depsToBuildDom.addChild(excludeKeysDom);
                        for (String key : sortAsString(depsToBuildConfig.getExcludeKeys())) {
                            final Xpp3Dom keyDom = newDom("key");
                            excludeKeysDom.addChild(keyDom);
                            keyDom.setValue(key);
                        }
                    }

                    configureInclusions(depsToBuildConfig, depsToBuildDom);
                }
            } else if (sbomConfig == null || sbomConfig.isApplyDependenciesToBuildInclusions()) {
                final Xpp3Dom depsToBuildDom = newDomSelfOverride(DEPENDENCIES_TO_BUILD);
                config.addChild(depsToBuildDom);
                ProjectDependencyFilterConfig depsToBuild = effectiveMemberDepsToBuildConfig(m.config());
                configureInclusions(depsToBuild, depsToBuildDom);
            } else {
                config.addChild(newDomSelfOverride(DEPENDENCIES_TO_BUILD));
            }
        }

        persistPom(pom);
    }

    private static SbomConfig.ProductConfig getProductInfo(PlatformMember member) {
        final SbomConfig.ProductConfig productConfig = member.config().getSbom() == null ? null
                : member.config().getSbom().getProductInfo();
        if (productConfig == null) {
            return null;
        }
        if (isBlank(productConfig.getType())) {
            productConfig.setType("FRAMEWORK");
        }
        if (isBlank(productConfig.getGroup())) {
            productConfig.setGroup(member.getGeneratedPlatformBom().getGroupId());
        }
        if (isBlank(productConfig.getName())) {
            productConfig.setName(member.getGeneratedPlatformBom().getArtifactId());
        }
        if (isBlank(productConfig.getVersion())) {
            productConfig.setVersion(member.getGeneratedPlatformBom().getVersion());
        }
        return productConfig;
    }

    private ProjectDependencyFilterConfig effectiveMemberDepsToBuildConfig(PlatformMemberConfig member) {
        var depsToBuild = new ProjectDependencyFilterConfig();
        if (dependenciesToBuild != null) {
            depsToBuild.merge(dependenciesToBuild);
        }
        if (member.getDependenciesToBuild() != null) {
            depsToBuild.merge(member.getDependenciesToBuild());
        }
        return depsToBuild;
    }

    private static void configureInclusions(ProjectDependencyFilterConfig depsToBuildConfig, Xpp3Dom depsToBuildDom) {
        if (depsToBuildConfig.getIncludeArtifacts().isEmpty()
                && depsToBuildConfig.getIncludeGroupIds().isEmpty()
                && depsToBuildConfig.getIncludeKeys().isEmpty()) {
            return;
        }
        final Xpp3Dom includeArtifactsDom = newDom("includeArtifacts");
        depsToBuildDom.addChild(includeArtifactsDom);
        if (!depsToBuildConfig.getIncludeArtifacts().isEmpty()) {
            for (var artifact : sortAsString(depsToBuildConfig.getIncludeArtifacts())) {
                includeArtifactsDom.addChild(textDomElement("artifact", artifact));
            }
        }

        if (!depsToBuildConfig.getIncludeGroupIds().isEmpty()) {
            final Xpp3Dom includeGroupIdsDom = newDom("includeGroupIds");
            depsToBuildDom.addChild(includeGroupIdsDom);
            for (String groupId : sortAsString(depsToBuildConfig.getIncludeGroupIds())) {
                includeGroupIdsDom.addChild(textDomElement("groupId", groupId));
            }
        }
        if (!depsToBuildConfig.getIncludeKeys().isEmpty()) {
            final Xpp3Dom includeKeysDom = newDom("includeKeys");
            depsToBuildDom.addChild(includeKeysDom);
            for (String key : sortAsString(depsToBuildConfig.getIncludeKeys())) {
                includeKeysDom.addChild(textDomElement("key", key));
            }
        }
    }

    private static <T> List<String> sortAsString(Collection<T> col) {
        var list = new ArrayList<String>(col.size());
        for (Object o : col) {
            list.add(String.valueOf(o));
        }
        Collections.sort(list);
        return list;
    }

    private static void generateReleasesReport(DecomposedBom originalBom, Path outputFile)
            throws MojoExecutionException {
        try {
            originalBom.visit(DecomposedBomHtmlReportGenerator.builder(outputFile)
                    .skipOriginsWithSingleRelease().build());
        } catch (BomDecomposerException e) {
            throw new MojoExecutionException("Failed to generate report " + outputFile, e);
        }
    }

    private static void generateBomReports(DecomposedBom originalBom, DecomposedBom generatedBom, Path outputDir,
            ReportIndexPageGenerator index, final Path platformBomXml, ArtifactResolver resolver)
            throws MojoExecutionException {
        final BomDiff.Config config = BomDiff.config();
        config.resolver(resolver);
        if (originalBom.bomResolver() != null && originalBom.bomResolver().isResolved()) {
            config.compare(originalBom.bomResolver().pomPath());
        } else {
            config.compare(originalBom.bomArtifact());
        }
        final BomDiff bomDiff = config.to(platformBomXml);

        final Path diffFile = outputDir.resolve("diff.html");
        HtmlBomDiffReportGenerator.config(diffFile).report(bomDiff);

        final Path generatedReleasesFile = outputDir.resolve("generated-releases.html");
        generateReleasesReport(generatedBom, generatedReleasesFile);

        final Path originalReleasesFile = outputDir.resolve("original-releases.html");
        generateReleasesReport(originalBom, originalReleasesFile);

        index.bomReport(bomDiff.mainUrl(), bomDiff.toUrl(), generatedBom, originalReleasesFile, generatedReleasesFile,
                diffFile);
    }

    private void addReleaseProfile(final Model pom) {
        final List<Profile> releaseProfiles = getGeneratedBomReleaseProfile();
        for (var p : releaseProfiles) {
            pom.addProfile(p);
        }
    }

    private void recordUpdatedBoms() throws MojoExecutionException {
        if (!recordUpdatedBoms) {
            return;
        }
        final int configIndex = pomLineContaining("platformConfig", 0);
        if (configIndex < 0) {
            throw new MojoExecutionException("Failed to locate <platformConfig> configuration in " + project.getFile());
        }
        final int coreIndex = pomLineContaining("<core>", configIndex);
        if (coreIndex < 0) {
            throw new MojoExecutionException("Failed to locate <core> configuration in " + project.getFile());
        }
        updatePreviousMemberRelease(quarkusCore, coreIndex);

        final int membersIndex = pomLineContaining("<members>", configIndex);
        if (membersIndex < 0) {
            throw new MojoExecutionException("Failed to locate <members> configuration in " + project.getFile());
        }

        for (PlatformMemberImpl member : members.values()) {
            if (!member.config().getName().equals(quarkusCore.config().getName())) {
                updatePreviousMemberRelease(member, membersIndex);
            }
        }

        if (pomLines != null) {
            final File outputDir = updatedPom.getParentFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            try {
                Files.write(updatedPom.toPath(), pomLines);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to persist changes to " + project.getFile(), e);
            }
            try {
                IoUtils.copy(updatedPom.toPath(), project.getFile().toPath());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to replace " + project.getFile() + " with " + updatedPom, e);
            }
            project.setPomFile(updatedPom);
        }
    }

    private void updatePreviousMemberRelease(PlatformMemberImpl member, int membersIndex) throws MojoExecutionException {
        if (member.bomChanged == null || !member.bomChanged || member.config().isHidden()) {
            return;
        }
        final int memberIndex = pomLineContaining("<name>" + member.config().getName() + "</name>", membersIndex);
        if (memberIndex < 0) {
            throw new MojoExecutionException(
                    "Failed to locate member configuration with <name>" + member.config().getName() + "</name>");
        }
        // the release element may contain combine.self attribute
        var releaseIndex = pomLineContaining("<release", memberIndex);
        if (releaseIndex < 0) {
            releaseIndex = memberIndex + 1;
            var sb = new StringBuilder();
            var nameLine = pomLines().get(memberIndex);
            for (int i = 0; i < nameLine.length(); ++i) {
                var ch = nameLine.charAt(i);
                if (Character.isWhitespace(ch)) {
                    sb.append(ch);
                } else {
                    break;
                }
            }
            pomLines().add(releaseIndex, sb + "<release combine.self=\"override\">");
            pomLines().add(releaseIndex + 1, sb + "</release>");
        }
        final int releaseEnd = pomLineContaining("</release>", releaseIndex);
        if (releaseEnd < 0) {
            throw new MojoExecutionException("Failed to locate the closing </release> for member with <name>"
                    + member.config().getName() + "</name>");
        }
        final String l = pomLines().get(releaseIndex);
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < l.length(); ++i) {
            if (!Character.isWhitespace(l.charAt(i))) {
                break;
            }
            buf.append(l.charAt(i));
        }
        var generatedBom = member.getAlignedDecomposedBom().bomArtifact();
        buf.append("    <lastDetectedBomUpdate>").append(generatedBom.getGroupId()).append(COLON)
                .append(generatedBom.getArtifactId()).append(COLON)
                .append(generatedBom.getVersion()).append("</lastDetectedBomUpdate>");
        int prevIndex = pomLineContaining("<lastDetectedBomUpdate>", releaseIndex, releaseEnd);
        if (prevIndex < 0) {
            pomLines().add(releaseIndex + 1, buf.toString());
        } else {
            pomLines().set(prevIndex, buf.toString());
        }
    }

    private void generateMavenPluginModule(Model parentPom) throws MojoExecutionException {

        final ArtifactCoords targetCoords = ArtifactCoords
                .fromString(platformConfig.getAttachedMavenPlugin().getTargetPluginCoords());

        final String moduleName = targetCoords.getArtifactId();
        parentPom.addModule(moduleName);

        if (platformConfig.getAttachedMavenPlugin().isImportSources()) {
            importOriginalPluginSources(parentPom, moduleName, platformConfig.getAttachedMavenPlugin(), targetCoords);
        } else {
            republishOriginalPluginBinary(parentPom, moduleName, targetCoords, ArtifactCoords
                    .fromString(platformConfig.getAttachedMavenPlugin().getOriginalPluginCoords()));
        }
    }

    private void importOriginalPluginSources(Model parentPom, final String moduleName,
            AttachedMavenPluginConfig pluginConfig, final ArtifactCoords targetCoords)
            throws MojoExecutionException {
        final ArtifactCoords originalCoords = ArtifactCoords.fromString(pluginConfig.getOriginalPluginCoords());
        final Path sourcesJar;
        try {
            sourcesJar = getNonWorkspaceResolver().resolve(new DefaultArtifact(originalCoords.getGroupId(),
                    originalCoords.getArtifactId(), "sources", ArtifactCoords.TYPE_JAR, originalCoords.getVersion()))
                    .getArtifact().getFile().toPath();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve the sources JAR of " + originalCoords, e);
        }

        final File pomXml = getPomFile(parentPom, moduleName);

        final Path baseDir = pomXml.getParentFile().toPath();
        final Path javaSources = baseDir.resolve("src").resolve("main").resolve("java");
        final Path resourcesDir = javaSources.getParent().resolve("resources");
        try {
            ZipUtils.unzip(sourcesJar, javaSources);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to unzip " + sourcesJar + " to " + javaSources, e);
        }

        // MOVE RESOURCES
        try (var stream = Files.list(javaSources)) {
            stream.forEach(p -> {
                if (p.getFileName().toString().equals("io")) {
                    return;
                }
                try {
                    IoUtils.copy(p, resourcesDir.resolve(p.getFileName()));
                    IoUtils.recursiveDelete(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            Path metainfDir = resourcesDir.resolve("META-INF");
            final Path mavenDir = metainfDir.resolve("maven");
            IoUtils.copy(
                    mavenDir.resolve(originalCoords.getGroupId()).resolve(originalCoords.getArtifactId()).resolve(POM_XML),
                    baseDir.resolve(POM_XML));
            IoUtils.recursiveDelete(mavenDir);
            IoUtils.recursiveDelete(metainfDir.resolve("INDEX.LIST"));
            IoUtils.recursiveDelete(metainfDir.resolve("MANIFEST.MF"));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to import original plugin sources", e);
        }

        // Delete the generated HelpMojo
        try {
            Files.walkFileTree(javaSources, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals("HelpMojo.java")) {
                        try {
                            Files.delete(file);
                        } catch (IOException ex) {
                        }
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to process " + javaSources, e);
        }

        final Model pom;
        try {
            pom = ModelUtils.readModel(pomXml.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + pomXml, e);
        }
        pom.setPomFile(pomXml);
        pom.setGroupId(targetCoords.getGroupId());
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(targetCoords.getArtifactId()));
        setParent(pom, parentPom);

        DependencyManagement dm = getOrCreateDependencyManagement(pom);
        final Artifact quarkusBom = quarkusCore.getGeneratedPlatformBom();
        final Dependency quarkusBomImport = new Dependency();
        quarkusBomImport.setGroupId(quarkusBom.getGroupId());
        quarkusBomImport.setArtifactId(quarkusBom.getArtifactId());
        quarkusBomImport.setType(ArtifactCoords.TYPE_POM);
        quarkusBomImport.setVersion(quarkusBom.getVersion());
        quarkusBomImport.setScope("import");
        dm.addDependency(quarkusBomImport);

        var build = getOrCreateBuild(pom);
        // copy the effective maven-plugin-plugin config and set expected versions
        final Model originalEffectiveModel = new EffectiveModelResolver(getNonWorkspaceResolver())
                .resolveEffectiveModel(originalCoords);
        var effectivePlugins = originalEffectiveModel.getBuild().getPlugins().stream()
                .collect(Collectors.toMap(Plugin::getArtifactId, p -> p));
        int i = 0;
        while (i < build.getPlugins().size()) {
            var plugin = build.getPlugins().get(i++);
            var effectivePlugin = effectivePlugins.get(plugin.getArtifactId());
            if (plugin.getArtifactId().equals("maven-plugin-plugin")) {
                build.getPlugins().set(i - 1, effectivePlugin);
            } else {
                plugin.setVersion(effectivePlugin.getVersion());
            }
        }

        // copy maven.* properties
        copyMavenCompilerProperties(originalEffectiveModel, pom);

        final Map<ArtifactKey, String> originalDepVersions = getVersionMap(originalEffectiveModel.getDependencies());
        final Map<ArtifactKey, String> managedDepVersions = getVersionMap(
                quarkusCore.generatedBomModel.getDependencyManagement().getDependencies());

        final List<Dependency> pluginDeps = pom.getDependencies();
        pom.setDependencies(new ArrayList<>(pluginDeps.size()));
        final List<org.eclipse.aether.graph.Dependency> directAetherDeps = new ArrayList<>(pluginDeps.size());
        for (Dependency d : pluginDeps) {
            if (TEST.equals(d.getScope())) {
                continue;
            }
            final ArtifactKey key = getKey(d);
            final String managedVersion = managedDepVersions.get(key);
            final String originalVersion = originalDepVersions.get(key);
            if (originalVersion == null) {
                throw new IllegalStateException(
                        "Failed to determine version for dependency " + d + " of the Maven plugin " + originalCoords);
            }
            if (d.getVersion() == null) {
                if (managedVersion == null) {
                    d.setVersion(getTestArtifactVersion(originalCoords.getGroupId(), originalVersion));
                }
            } else if (d.getVersion().startsWith("${")) {
                if (originalVersion.equals(managedVersion)) {
                    d.setVersion(null);
                } else {
                    d.setVersion(getTestArtifactVersion(originalCoords.getGroupId(), originalVersion));
                }
            }
            pom.addDependency(d);
            directAetherDeps.add(getAlignedAetherDependency(d, managedVersion, originalVersion));
        }

        // make sure the original properties do not override the platform ones
        var originalProps = pom.getProperties();
        if (!originalProps.isEmpty()) {
            pom.setProperties(new Properties());
            for (Map.Entry<?, ?> originalProp : originalProps.entrySet()) {
                final String propName = originalProp.getKey().toString();
                // if it's not a version property, we add it
                if (propName.startsWith("maven.") || getArtifactGroupIdsForVersionProperty(propName).isEmpty()) {
                    pom.getProperties().setProperty(propName, originalProp.getValue().toString());
                }
            }
        }

        if (pluginConfig.isFlattenPom()) {
            configureFlattenPlugin(pom, false, Map.of("dependencyManagement", "keep"));
        }

        var testConfig = platformConfig.getAttachedMavenPlugin().getTest();
        if (testConfig != null && !testConfig.isExcluded()) {
            var testArtifact = ArtifactCoords.fromString(testConfig.getArtifact());
            final Set<ArtifactKey> bannedTestDependencyKeys = collectDependencyKeys(pom, directAetherDeps);
            bannedTestDependencyKeys.add(testArtifact.getKey());
            configureTests(testArtifact, testConfig, pom, bannedTestDependencyKeys);
        }
        persistPom(pom);
    }

    private Set<ArtifactKey> collectDependencyKeys(Model pom, List<org.eclipse.aether.graph.Dependency> directAetherDeps) {
        final DependencyNode root;
        try {
            root = getNonWorkspaceResolver().getSystem().collectDependencies(
                    getNonWorkspaceResolver().getSession(),
                    MavenArtifactResolver.newCollectRequest(
                            new DefaultArtifact(ModelUtils.getGroupId(pom), pom.getArtifactId(), ArtifactCoords.TYPE_POM,
                                    ModelUtils.getVersion(pom)),
                            directAetherDeps, toAetherDependencies(pom.getDependencyManagement().getDependencies()),
                            List.of(), getNonWorkspaceResolver().getRepositories()))
                    .getRoot();
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
        final Set<ArtifactKey> bannedTestDependencyKeys = new HashSet<>();
        collectDependencyKeys(root, bannedTestDependencyKeys);
        return bannedTestDependencyKeys;
    }

    private static void collectDependencyKeys(DependencyNode node, Set<ArtifactKey> keys) {
        var a = node.getArtifact();
        if (a != null) {
            keys.add(ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension()));
        }
        for (var c : node.getChildren()) {
            collectDependencyKeys(c, keys);
        }
    }

    private org.eclipse.aether.graph.Dependency getAlignedAetherDependency(Dependency d, String managedVersion,
            String originalVersion) {
        var aetherDep = RepositoryUtils.toDependency(d,
                getNonWorkspaceResolver().getSession().getArtifactTypeRegistry());
        var version = managedVersion == null ? originalVersion : managedVersion;
        if (!version.equals(aetherDep.getArtifact().getVersion())) {
            aetherDep = aetherDep.setArtifact(aetherDep.getArtifact().setVersion(version));
        }
        return aetherDep;
    }

    private static Map<ArtifactKey, String> getVersionMap(List<Dependency> deps) {
        final Map<ArtifactKey, String> originalDepVersions = new HashMap<>(deps.size());
        for (Dependency d : deps) {
            originalDepVersions.put(ArtifactKey.of(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType()),
                    d.getVersion());
        }
        return originalDepVersions;
    }

    private static void copyMavenCompilerProperties(Model srcModel, Model targetModel) {
        var srcProps = srcModel.getProperties();
        for (var propName : srcProps.stringPropertyNames()) {
            if (propName.startsWith("maven.compiler.")) {
                targetModel.getProperties().setProperty(propName, srcProps.getProperty(propName));
            }
        }
    }

    private void configureFlattenPlugin(Model pom, boolean updatePomFile, Map<String, String> elementConfig) {
        Build build = getOrCreateBuild(pom);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("org.codehaus.mojo");
        plugin.setArtifactId("flatten-maven-plugin");
        PluginExecution e = new PluginExecution();
        plugin.addExecution(e);
        e.setId("flatten");
        e.setPhase("process-resources");
        e.addGoal("flatten");
        Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
        e.setConfiguration(config);

        config.addChild(textDomElement("flattenMode", "oss"));

        if (updatePomFile) {
            config.addChild(textDomElement("updatePomFile", "true"));
        }

        Xpp3Dom pomElements = new Xpp3Dom("pomElements");
        config.addChild(pomElements);
        pomElements.addChild(textDomElement("build", "remove"));
        pomElements.addChild(textDomElement("repositories", "remove"));
        final List<String> elementNames = new ArrayList<>(elementConfig.keySet());
        Collections.sort(elementNames);
        for (String elementName : elementNames) {
            pomElements.addChild(textDomElement(elementName, elementConfig.get(elementName)));
        }

        e = new PluginExecution();
        plugin.addExecution(e);
        e.setId("flatten.clean");
        e.setPhase("clean");
        e.addGoal("clean");
    }

    private static Xpp3Dom textDomElement(String name, String value) {
        final Xpp3Dom e = new Xpp3Dom(name);
        e.setValue(value);
        return e;
    }

    private static Xpp3Dom newDomChildrenAppend(String name) {
        final Xpp3Dom dom = newDom(name);
        dom.setAttribute("combine.children", "append");
        return dom;
    }

    private static Xpp3Dom newDomSelfAppend(String name) {
        final Xpp3Dom dom = newDom(name);
        dom.setAttribute("combine.self", "append");
        return dom;
    }

    private static Xpp3Dom newDomSelfOverride(String name) {
        final Xpp3Dom dom = newDom(name);
        dom.setAttribute("combine.self", "override");
        return dom;
    }

    private static Xpp3Dom newDom(String name) {
        return new Xpp3Dom(name);
    }

    private static File getPomFile(Model parentPom, final String moduleName) {
        return new File(new File(parentPom.getProjectDirectory(), moduleName), POM_XML);
    }

    private void republishOriginalPluginBinary(Model parentPom, final String moduleName,
            final ArtifactCoords targetCoords, final ArtifactCoords originalCoords) throws MojoExecutionException {
        final Model pom = newModel();
        if (!targetCoords.getGroupId().equals(project.getGroupId())) {
            pom.setGroupId(targetCoords.getGroupId());
        }
        pom.setArtifactId(moduleName);
        if (!targetCoords.getVersion().equals(project.getVersion())) {
            pom.setVersion(targetCoords.getVersion());
        }

        pom.setPackaging("maven-plugin");
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(targetCoords.getArtifactId()));

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);

        setParent(pom, parentPom);

        DependencyManagement dm = getOrCreateDependencyManagement(pom);
        final Artifact quarkusBom = quarkusCore.getGeneratedPlatformBom();
        final Dependency quarkusBomImport = new Dependency();
        quarkusBomImport.setGroupId(quarkusBom.getGroupId());
        quarkusBomImport.setArtifactId(quarkusBom.getArtifactId());
        quarkusBomImport.setType(ArtifactCoords.TYPE_POM);
        quarkusBomImport.setVersion(quarkusBom.getVersion());
        quarkusBomImport.setScope("import");
        dm.addDependency(quarkusBomImport);

        final Build build = getOrCreateBuild(pom);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("generate-resources");
        exec.addGoal("attach-maven-plugin");
        Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
        exec.setConfiguration(config);
        config.addChild(
                textDomElement("originalPluginCoords", platformConfig.getAttachedMavenPlugin().getOriginalPluginCoords()));
        config.addChild(textDomElement("targetPluginCoords", platformConfig.getAttachedMavenPlugin().getTargetPluginCoords()));

        // keep the previous plugin-help.xml path to avoid re-compiling the HelpMojo
        plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("com.coderplus.maven.plugins");
        plugin.setArtifactId("copy-rename-maven-plugin");
        plugin.setVersion("1.0");
        exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setId("copy-plugin-help");
        exec.setPhase("process-classes");
        exec.addGoal("copy");
        config = new Xpp3Dom(CONFIGURATION);
        exec.setConfiguration(config);
        config.addChild(textDomElement("sourceFile",
                "${project.build.outputDirectory}/META-INF/maven/" + targetCoords.getGroupId() + "/"
                        + targetCoords.getArtifactId() + "/plugin-help.xml"));
        config.addChild(textDomElement("destinationFile", "${project.build.outputDirectory}/META-INF/maven/"
                + originalCoords.getGroupId() + "/" + originalCoords.getArtifactId() + "/plugin-help.xml"));

        persistPom(pom);
    }

    private void generateMavenRepoZipModule(Model parentPom) throws MojoExecutionException {
        final Model pom = newModel();
        final String artifactId = "maven-repo-zip-generator";
        pom.setArtifactId(artifactId);
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(artifactId));
        parentPom.addModule(artifactId);
        final File pomXml = getPomFile(parentPom, artifactId);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);
        Utils.skipInstallAndDeploy(pom);

        Build build = getOrCreateBuild(pom);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        Profile profile = new Profile();
        profile.setId("mavenRepoZip");
        final ActivationProperty activationProperty = new ActivationProperty();
        activationProperty.setName("mavenRepoZip");
        final Activation activation = new Activation();
        activation.setProperty(activationProperty);
        profile.setActivation(activation);
        pom.addProfile(profile);

        build = getOrCreateBuild(profile);

        final StringBuilder sb = new StringBuilder();
        for (PlatformMemberImpl m : members.values()) {
            if (!m.config.isHidden() && m.config.isEnabled()) {
                sb.append("quarkus-platform-bom:generate-maven-repo-zip@").append(m.getConfiguredPlatformBom().getArtifactId())
                        .append(' ');
            }
        }
        build.setDefaultGoal(sb.toString());

        PluginManagement pluginMgmt = new PluginManagement();
        build.setPluginManagement(pluginMgmt);

        plugin = new Plugin();
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        pluginMgmt.addPlugin(plugin);

        final GenerateMavenRepoZip generateMavenRepoZip = platformConfig.getGenerateMavenRepoZip();

        Path repoOutputDir = buildDir.toPath().resolve("maven-repo");
        repoOutputDir = pomXml.toPath().getParent().getParent().relativize(repoOutputDir);

        for (PlatformMember m : members.values()) {
            if (!m.config().isEnabled() || m.config().isHidden()) {
                continue;
            }

            final PluginExecution exec = new PluginExecution();
            plugin.addExecution(exec);
            exec.setId(m.getConfiguredPlatformBom().getArtifactId());
            exec.setPhase("process-resources");
            exec.addGoal("generate-maven-repo-zip");

            Xpp3Dom e = new Xpp3Dom("generateMavenRepoZip");

            final Xpp3Dom bom = new Xpp3Dom("bom");
            bom.setValue(m.getGeneratedPlatformBom().toString());
            e.addChild(bom);

            if (generateMavenRepoZip.getRepositoryDir() != null) {
                e.addChild(textDomElement("repositoryDir", generateMavenRepoZip.getRepositoryDir()));
            }

            final Path repoDir = repoOutputDir.resolve(m.getConfiguredPlatformBom().getArtifactId());
            e.addChild(textDomElement("repositoryDir", repoDir.toString()));
            e.addChild(textDomElement("zipLocation",
                    repoDir + "/" + m.getConfiguredPlatformBom().getArtifactId() + "-maven-repo.zip"));

            if (generateMavenRepoZip.getIncludedVersionsPattern() != null) {
                e.addChild(textDomElement("includedVersionsPattern", generateMavenRepoZip.getIncludedVersionsPattern()));
            } else if (platformConfig.getBomGenerator() != null
                    && !platformConfig.getBomGenerator().versionConstraintPreferences.isEmpty()) {
                if (platformConfig.getBomGenerator().versionConstraintPreferences.size() != 1) {
                    throw new MojoExecutionException(
                            "Found more than one includedVersionsPattern for the Maven repo generator: "
                                    + platformConfig.getBomGenerator().versionConstraintPreferences);
                }
                e.addChild(textDomElement("includedVersionsPattern",
                        platformConfig.getBomGenerator().versionConstraintPreferences.get(0)));
            }

            if (!generateMavenRepoZip.getExcludedGroupIds().isEmpty()) {
                final Xpp3Dom d = new Xpp3Dom("excludedGroupIds");
                for (String groupId : generateMavenRepoZip.getExcludedGroupIds()) {
                    d.addChild(textDomElement("groupId", groupId));
                }
                e.addChild(d);
            }
            if (!generateMavenRepoZip.getExcludedArtifacts().isEmpty()) {
                final Xpp3Dom d = new Xpp3Dom("excludedArtifacts");
                for (String key : generateMavenRepoZip.getExcludedArtifacts()) {
                    d.addChild(textDomElement("key", key));
                }
                e.addChild(d);
            }
            if (!generateMavenRepoZip.getExtraArtifacts().isEmpty()) {
                final Xpp3Dom extras = new Xpp3Dom("extraArtifacts");
                for (String coords : generateMavenRepoZip.getExtraArtifacts()) {
                    extras.addChild(textDomElement("artifact", coords));
                }
                extras.addChild(textDomElement("artifact", m.descriptorCoords().toString()));
                extras.addChild(textDomElement("artifact", m.propertiesCoords().toString()));
                e.addChild(extras);
            }
            if (generateMavenRepoZip.getIncludedVersionsPattern() != null) {
                e.addChild(textDomElement("includedVersionsPattern", generateMavenRepoZip.getIncludedVersionsPattern()));
            }

            final Xpp3Dom configuration = new Xpp3Dom(CONFIGURATION);
            configuration.addChild(e);
            exec.setConfiguration(configuration);

        }
        persistPom(pom);
    }

    private void generateMemberModule(PlatformMemberImpl member, String moduleName, Model parentPom)
            throws MojoExecutionException {

        final Model pom = newModel();

        if (!member.getGeneratedPlatformBom().getGroupId().equals(project.getGroupId())) {
            pom.setGroupId(member.getGeneratedPlatformBom().getGroupId());
        }
        pom.setArtifactId(moduleName + "-parent");
        if (!member.getGeneratedPlatformBom().getVersion().equals(project.getVersion())) {
            pom.setVersion(member.getGeneratedPlatformBom().getVersion());
        }

        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + member.config().getName() + " - Parent");

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);

        member.baseModel = pom;

        if (member.config().isHidden()
                || platformConfig.getRelease() != null
                        && platformConfig.getRelease().isOnlyChangedMembers()
                        && member.getGeneratedPlatformBom().equals(member.previousLastUpdatedBom())) {
            getLog().info("Excluding " + member.getGeneratedPlatformBom() + " from the upcoming release");
            Utils.skipInstallAndDeploy(pom);
        }
        persistPom(pom);
    }

    private void generateMemberBom(PlatformMemberImpl member) throws MojoExecutionException {
        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(member.baseModel) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        member.baseModel.addModule(moduleName);
        final Path platformBomXml = member.baseModel.getProjectDirectory().toPath().resolve(moduleName).resolve(POM_XML);
        member.generatedBomModel = PlatformBomUtils.toPlatformModel(member.generatedBom, baseModel, catalogResolver());
        addReleaseProfile(member.generatedBomModel);

        if (member.config().isHidden()) {
            Utils.skipInstallAndDeploy(member.generatedBomModel);
        }

        try {
            Files.createDirectories(platformBomXml.getParent());
            ModelUtils.persistModel(platformBomXml, member.generatedBomModel);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist generated BOM to " + platformBomXml, e);
        }

        member.generatedPomFile = platformBomXml;

        if (recordUpdatedBoms) {
            isChangedSinceLastRelease(member);
        }
    }

    private boolean isChangedSinceLastRelease(PlatformMemberImpl member) throws MojoExecutionException {
        if (member.bomChanged == null) {
            final Artifact prevBomCoords = member.previousLastUpdatedBom();
            if (prevBomCoords == null) {
                member.bomChanged = true;
            } else {
                var prevCoords = ArtifactCoords.pom(prevBomCoords.getGroupId(), prevBomCoords.getArtifactId(),
                        prevBomCoords.getVersion());
                var generatedCoords = ArtifactCoords.pom(member.getAlignedDecomposedBom().bomArtifact().getGroupId(),
                        member.getAlignedDecomposedBom().bomArtifact().getArtifactId(),
                        member.getAlignedDecomposedBom().bomArtifact().getVersion());
                if (!prevCoords.equals(generatedCoords)) {
                    final List<org.eclipse.aether.graph.Dependency> prevDeps;
                    try {
                        prevDeps = getNonWorkspaceResolver().resolveDescriptor(prevBomCoords).getManagedDependencies();
                    } catch (BootstrapMavenException e) {
                        throw new MojoExecutionException("Failed to resolve " + prevBomCoords, e);
                    }
                    if (prevDeps.isEmpty()) {
                        // failed to resolve
                        member.bomChanged = true;
                    } else {
                        final Set<ArtifactCoords> prevArtifacts = new HashSet<>(prevDeps.size());
                        for (var d : prevDeps) {
                            if (isMeaningfulClasspathConstraint(d.getArtifact())) {
                                prevArtifacts.add(toCoords(d.getArtifact()));
                            }
                        }

                        final Set<ArtifactCoords> currentArtifacts = new HashSet<>(prevArtifacts.size());
                        for (ProjectRelease r : member.generatedBom.releases()) {
                            for (ProjectDependency d : r.dependencies()) {
                                if (isMeaningfulClasspathConstraint(d.artifact())) {
                                    currentArtifacts.add(toCoords(d.artifact()));
                                }
                            }
                        }
                        member.bomChanged = !prevArtifacts.equals(currentArtifacts);
                    }
                } else {
                    member.bomChanged = false;
                }
            }
        }
        return member.bomChanged;
    }

    private List<Profile> getGeneratedBomReleaseProfile() {
        if (generatedBomReleaseProfile == null) {
            generatedBomReleaseProfile = new ArrayList<>(2);
            for (Profile profile : project.getModel().getProfiles()) {
                if (profile.getId().startsWith("release")) {
                    generatedBomReleaseProfile.add(copyReleasePlugins(profile));
                }
            }
        }
        return generatedBomReleaseProfile;
    }

    private Profile copyReleasePlugins(Profile parentReleaseProfile) {
        final Profile memberReleaseProfile = new Profile();
        memberReleaseProfile.setId(parentReleaseProfile.getId());
        memberReleaseProfile.setActivation(parentReleaseProfile.getActivation());
        if (parentReleaseProfile.getBuild() != null) {
            final Build build = getOrCreateBuild(memberReleaseProfile);
            build.setPluginManagement(parentReleaseProfile.getBuild().getPluginManagement());
            for (Plugin plugin : parentReleaseProfile.getBuild().getPlugins()) {
                if (plugin.getArtifactId().equals("maven-gpg-plugin")) {
                    build.addPlugin(clonePluginConfig(plugin));
                } else if (plugin.getArtifactId().equals("nexus-staging-maven-plugin")) {
                    build.addPlugin(clonePluginConfig(plugin));
                }
            }
        }
        return memberReleaseProfile;
    }

    private Plugin clonePluginConfig(Plugin plugin) {
        String pluginVersion = plugin.getVersion();
        if (pluginVersion == null) {
            final Plugin managedPlugin = project.getPluginManagement().getPluginsAsMap().get(plugin.getKey());
            if (managedPlugin == null || managedPlugin.getVersion() == null) {
                getLog().warn("Failed to determine the version for " + plugin.getKey());
            } else {
                pluginVersion = getPreferredVersionValue(managedPlugin.getVersion(), false);
            }
        } else {
            pluginVersion = getPreferredVersionValue(pluginVersion, false);
        }
        if (!Objects.equals(pluginVersion, plugin.getVersion())) {
            plugin = plugin.clone();
            plugin.setVersion(pluginVersion);
        }
        return plugin;
    }

    private String getPreferredVersionValue(String version, boolean preferVersionProperty) {
        if (version.startsWith("${") && version.endsWith("}")) {
            if (!preferVersionProperty) {
                var value = project.getProperties().getProperty(version.substring(2, version.length() - 1));
                if (value != null) {
                    version = value;
                }
            }
        } else if (preferVersionProperty) {
            version = toPropertyOrSelf(version);
        }
        return version;
    }

    private String toPropertyOrSelf(String version) {
        var prop = pomPropsByValues.get(version);
        return prop == null ? version : "${" + prop + "}";
    }

    private static ArtifactCoords toCoords(final Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private static boolean isMeaningfulClasspathConstraint(final Artifact a) {
        return a.getExtension().equals(ArtifactCoords.TYPE_JAR)
                && !(PlatformArtifacts.isCatalogArtifactId(a.getArtifactId())
                        || a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                        || "sources".equals(a.getClassifier())
                        || "javadoc".equals(a.getClassifier()));
    }

    private List<String> pomLines() {
        if (pomLines != null) {
            return pomLines;
        }
        try {
            return pomLines = Files.readAllLines(project.getFile().toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + project.getFile(), e);
        }
    }

    private int pomLineContaining(String text, int fromLine) {
        return pomLineContaining(text, fromLine, Integer.MAX_VALUE);
    }

    private int pomLineContaining(String text, int fromLine, int toLine) {
        final List<String> lines = pomLines();
        final int upperLimit = Math.min(lines.size(), toLine);
        while (fromLine < upperLimit) {
            if (lines.get(fromLine).contains(text)) {
                break;
            }
            ++fromLine;
        }
        return fromLine == upperLimit ? -1 : fromLine;
    }

    private void generateMemberIntegrationTestsModule(PlatformMemberImpl member, PlatformGenTaskScheduler scheduler)
            throws Exception {

        final Model parentPom = member.baseModel;
        final String moduleName = "integration-tests";

        final Model pom = newModel();
        pom.setArtifactId(getArtifactIdBase(parentPom) + "-" + moduleName + "-parent");
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(moduleName) + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);

        final DependencyManagement dm = getOrCreateDependencyManagement(pom);

        Dependency managedDep = getUniversalBomImport();
        dm.addDependency(managedDep);

        managedDep = new Dependency();
        managedDep.setGroupId("io.quarkus");
        managedDep.setArtifactId("quarkus-integration-test-class-transformer");
        managedDep.setVersion(quarkusCore.getVersionProperty());
        dm.addDependency(managedDep);
        managedDep = new Dependency();
        managedDep.setGroupId("io.quarkus");
        managedDep.setArtifactId("quarkus-integration-test-class-transformer-deployment");
        managedDep.setVersion(quarkusCore.getVersionProperty());
        dm.addDependency(managedDep);

        final Map<ArtifactKey, PlatformMemberTestConfig> testConfigs = new LinkedHashMap<>();
        for (PlatformMemberTestConfig test : member.config().getTests()) {
            testConfigs.put(ArtifactCoords.fromString(test.getArtifact()).getKey(), test);
        }

        if (member.config().getTestCatalogArtifact() != null) {
            final Artifact testCatalogArtifact = toAetherArtifact(member.config().getTestCatalogArtifact());
            final File testCatalogFile;
            try {
                testCatalogFile = getNonWorkspaceResolver().resolve(testCatalogArtifact).getArtifact().getFile();
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to resolve test catalog artifact " + testCatalogArtifact, e);
            }

            final Document testCatalogDoc;
            try (BufferedReader reader = Files.newBufferedReader(testCatalogFile.toPath())) {
                final Builder parser = new Builder();
                testCatalogDoc = parser.build(reader);
            } catch (Exception ex) {
                throw new MojoExecutionException("Failed to parse " + testCatalogFile, ex);
            }
            for (Element testElement : testCatalogDoc.getRootElement().getChildElements("testArtifact")) {
                final String testGroupId = testElement.getFirstChildElement("groupId").getValue();
                if (testGroupId == null) {
                    throw new MojoExecutionException(
                            "Test catalog " + testCatalogFile + " contains an artifact with a missing groupId " + testElement);
                }
                final String testArtifactId = testElement.getFirstChildElement("artifactId").getValue();
                if (testArtifactId == null) {
                    throw new MojoExecutionException("Test catalog " + testCatalogFile
                            + " contains an artifact with a missing artifactId " + testElement);
                }
                final Element versionElement = testElement.getFirstChildElement("version");
                String testVersion = versionElement == null ? null : versionElement.getValue();
                if (testVersion == null || testVersion.isBlank()) {
                    testVersion = testCatalogArtifact.getVersion();
                }
                final ArtifactCoords testCoords = ArtifactCoords.jar(testGroupId, testArtifactId, testVersion);
                // add it unless it's overriden in the config
                PlatformMemberTestConfig testConfig = testConfigs.get(testCoords.getKey());
                if (testConfig == null) {
                    testConfig = new PlatformMemberTestConfig();
                    testConfig.setArtifact(
                            testCoords.getGroupId() + COLON + testCoords.getArtifactId() + COLON + testCoords.getVersion());
                    testConfigs.put(testCoords.getKey(), testConfig);
                }
            }
        }

        for (PlatformMemberTestConfig testConfig : testConfigs.values()) {
            if (member.config().getDefaultTestConfig() != null) {
                testConfig.applyDefaults(member.config().getDefaultTestConfig());
            }
            if (!testConfig.isExcluded()) {
                var testArtifact = ArtifactCoords.fromString(testConfig.getArtifact());
                final String testModuleName;
                if (pom.getModules().contains(testArtifact.getArtifactId())) {
                    String tmp = testArtifact.getArtifactId() + "-" + testArtifact.getVersion();
                    if (pom.getModules().contains(tmp)) {
                        throw new MojoExecutionException("The same test " + testArtifact + " appears to be added twice");
                    }
                    testModuleName = tmp;
                    getLog().warn("Using " + testModuleName + " as the module name for " + testArtifact + " since "
                            + testArtifact.getArtifactId() + " module name already exists");
                } else {
                    testModuleName = testArtifact.getArtifactId();
                }
                pom.addModule(testModuleName);
                scheduler.schedule(() -> generateIntegrationTestModule(testModuleName, testArtifact, testConfig, pom));
            }
        }

        scheduler.addFinializingTask(() -> {
            Utils.skipInstallAndDeploy(pom);
            persistPom(pom);
        });
    }

    private Dependency getUniversalBomImport() {
        Dependency bomDep = new Dependency();
        final Artifact bom = getUniversalBomArtifact();
        bomDep.setGroupId(bom.getGroupId());
        bomDep.setArtifactId(bom.getArtifactId());
        bomDep.setVersion(getUniversalBomArtifact().getVersion());
        bomDep.setType(ArtifactCoords.TYPE_POM);
        bomDep.setScope("import");
        return bomDep;
    }

    private void generateIntegrationTestModule(String moduleName, ArtifactCoords testArtifact,
            PlatformMemberTestConfig testConfig, Model parentPom) throws MojoExecutionException {

        final Model pom = newModel();
        pom.setArtifactId(moduleName);
        pom.setName(getNameBase(parentPom) + " " + moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);

        configureTests(testArtifact, testConfig, pom, Set.of());

        Utils.disablePlugin(pom, "maven-jar-plugin", "default-jar");
        Utils.disablePlugin(pom, "maven-source-plugin", "attach-sources");
        persistPom(pom);

        if (testConfig.getTransformWith() != null) {
            final Path xsl = Path.of(testConfig.getTransformWith()).toAbsolutePath();
            if (!Files.exists(xsl)) {
                throw new MojoExecutionException("Failed to locate " + xsl);
            }

            final File transformedPom = new File(pomXml.getParent(), "transformed-pom.xml");

            final Source xslt = new StreamSource(xsl.toFile());
            final Source xml = new StreamSource(pomXml);
            final Result out = new StreamResult(transformedPom);

            final TransformerFactory factory = getTransformerFactory();

            try {
                final Transformer transformer = factory.newTransformer(xslt);
                transformer.transform(xml, out);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to transform " + pomXml + " with " + xsl, e);
            }

            try {
                Files.move(transformedPom.toPath(), pomXml.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to move " + transformedPom + " to " + pomXml, e);
            }
        }

        final Path seed = pom.getProjectDirectory().toPath().resolve("src").resolve("main").resolve("resources")
                .resolve("seed");
        try {
            Files.createDirectories(seed.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(seed)) {
                writer.write("seed");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create file " + seed, e);
        }
    }

    private void configureTests(ArtifactCoords testArtifact, PlatformMemberTestConfig testConfig, Model pom,
            Set<ArtifactKey> bannedDependencyKeys)
            throws MojoExecutionException {
        if (!testConfig.getPomProperties().isEmpty()) {
            pom.setProperties(testConfig.getPomProperties());
        }
        if (testConfig.isSkip()) {
            pom.getProperties().setProperty("maven.test.skip", "true");
        }

        final String testArtifactVersion = getTestArtifactVersion(testArtifact.getGroupId(), testArtifact.getVersion());

        final Dependency appArtifactDep = new Dependency();
        appArtifactDep.setGroupId(testArtifact.getGroupId());
        appArtifactDep.setArtifactId(testArtifact.getArtifactId());
        if (!testArtifact.getClassifier().isEmpty()) {
            appArtifactDep.setClassifier(testArtifact.getClassifier());
        }
        appArtifactDep.setType(testArtifact.getType());
        appArtifactDep.setVersion(testArtifactVersion);
        if (!bannedDependencyKeys.contains(testArtifact.getKey())) {
            pom.addDependency(appArtifactDep);
        }

        final Dependency testArtifactDep = new Dependency();
        testArtifactDep.setGroupId(testArtifact.getGroupId());
        testArtifactDep.setArtifactId(testArtifact.getArtifactId());
        testArtifactDep.setClassifier("tests");
        testArtifactDep.setType("test-jar");
        testArtifactDep.setVersion(testArtifactVersion);
        testArtifactDep.setScope(TEST);
        pom.addDependency(testArtifactDep);

        addDependencies(pom, testConfig.getDependencies(), false);
        addDependencies(pom, testConfig.getTestDependencies(), true);

        final Xpp3Dom depsToScan = new Xpp3Dom("dependenciesToScan");
        depsToScan.addChild(textDomElement("dependency", testArtifact.getGroupId() + COLON + testArtifact.getArtifactId()));

        if (!testConfig.isSkipJvm()) {
            Build build = getOrCreateBuild(pom);

            if (testConfig.isMavenFailsafePlugin()) {
                build.addPlugin(createFailsafeConfig(testConfig, depsToScan, false));
            } else {
                final Plugin plugin = getOrCreatePlugin(build, "org.apache.maven.plugins", "maven-surefire-plugin");
                final Xpp3Dom config = getOrCreateConfiguration(plugin);
                config.addChild(depsToScan);

                if (!testConfig.getSystemProperties().isEmpty()) {
                    addMapConfig(getOrCreateChild(config, SYSTEM_PROPERTY_VARIABLES), testConfig.getSystemProperties());
                }
                if (!testConfig.getJvmSystemProperties().isEmpty()) {
                    addMapConfig(getOrCreateChild(config, SYSTEM_PROPERTY_VARIABLES), testConfig.getJvmSystemProperties());
                }

                if (!testConfig.getEnvironmentVariables().isEmpty()) {
                    addMapConfig(getOrCreateChild(config, ENVIRONMENT_VARIABLES), testConfig.getEnvironmentVariables());
                }
                if (!testConfig.getJvmEnvironmentVariables().isEmpty()) {
                    addMapConfig(getOrCreateChild(config, ENVIRONMENT_VARIABLES), testConfig.getJvmEnvironmentVariables());
                }

                addGroupsConfig(testConfig, config, false);
                addIncludesExcludesConfig(testConfig, config, false);
                if (testConfig.getJvmArgLine() != null) {
                    config.addChild(textDomElement(ARG_LINE, testConfig.getJvmArgLine()));
                } else if (testConfig.getArgLine() != null) {
                    config.addChild(textDomElement(ARG_LINE, testConfig.getArgLine()));
                }

                if (testConfig.getJvmTestPattern() != null) {
                    config.addChild(textDomElement(TEST, testConfig.getJvmTestPattern()));
                } else if (testConfig.getTestPattern() != null) {
                    config.addChild(textDomElement(TEST, testConfig.getTestPattern()));
                }
            }

            try {
                for (org.eclipse.aether.graph.Dependency d : getNonWorkspaceResolver()
                        .resolveDescriptor(toPomArtifact(testArtifact)).getDependencies()) {
                    if (!d.getScope().equals(TEST)) {
                        continue;
                    }
                    final Artifact a = d.getArtifact();
                    final ArtifactKey depKey = ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(),
                            a.getExtension());
                    if (bannedDependencyKeys.contains(depKey)) {
                        continue;
                    }

                    // filter out pom dependencies with *:* exclusions
                    if (ArtifactCoords.TYPE_POM.equals(a.getExtension()) && !d.getExclusions().isEmpty()) {
                        boolean skip = false;
                        for (org.eclipse.aether.graph.Exclusion e : d.getExclusions()) {
                            if ("*".equals(e.getGroupId()) && "*".equals(e.getArtifactId())) {
                                skip = true;
                                break;
                            }
                        }
                        if (skip) {
                            continue;
                        }
                    }

                    final Dependency modelDep = new Dependency();
                    modelDep.setGroupId(a.getGroupId());
                    modelDep.setArtifactId(a.getArtifactId());
                    if (!a.getClassifier().isEmpty()) {
                        modelDep.setClassifier(a.getClassifier());
                    }
                    modelDep.setType(a.getExtension());
                    if (!universalBomDepKeys.containsKey(depKey)) {
                        modelDep.setVersion(getTestArtifactVersion(a.getGroupId(), a.getVersion()));
                    }
                    modelDep.setScope(d.getScope());
                    if (d.getOptional() != null) {
                        modelDep.setOptional(d.getOptional());
                    }
                    if (!d.getExclusions().isEmpty()) {
                        for (org.eclipse.aether.graph.Exclusion e : d.getExclusions()) {
                            final Exclusion modelEx = new Exclusion();
                            modelEx.setGroupId(e.getGroupId());
                            modelEx.setArtifactId(e.getArtifactId());
                            modelDep.addExclusion(modelEx);
                        }
                    }
                    pom.addDependency(modelDep);
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to describe " + testArtifact, e);
            }
        }

        // NATIVE
        if (!testConfig.isSkipNative()) {
            final Profile profile = new Profile();
            pom.addProfile(profile);
            profile.setId("native-image");
            final Activation activation = new Activation();
            profile.setActivation(activation);
            final ActivationProperty prop = new ActivationProperty();
            activation.setProperty(prop);
            prop.setName("native");
            profile.addProperty("quarkus.package.type", "native");
            final BuildBase buildBase = getOrCreateBuild(profile);

            buildBase.addPlugin(createFailsafeConfig(testConfig, depsToScan, true));

            Plugin plugin = new Plugin();
            buildBase.addPlugin(plugin);
            plugin.setGroupId("io.quarkus");
            plugin.setArtifactId("quarkus-maven-plugin");
            plugin.setVersion(quarkusCore.getVersionProperty());
            PluginExecution exec = new PluginExecution();
            plugin.addExecution(exec);
            exec.setId("native-image");
            exec.addGoal("build");

            Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
            exec.setConfiguration(config);
            if (testConfig.isSkip()) {
                config.addChild(textDomElement("skip", "true"));
            }
            config.addChild(textDomElement("appArtifact",
                    testArtifact.getGroupId() + COLON + testArtifact.getArtifactId() + COLON + testArtifactVersion));
        }

        if (testConfig.isPackageApplication()) {
            addQuarkusBuildConfig(pom, appArtifactDep);
        }

        if (!testConfig.getCopyTasks().isEmpty()) {
            for (Copy copy : testConfig.getCopyTasks()) {
                final Path src = Path.of(copy.getSrc());
                if (!Files.exists(src)) {
                    throw new MojoExecutionException(
                            "Failed to generate test module for " + testConfig.getArtifact() + ": couldn't copy "
                                    + copy.getSrc() + " to " + copy.getDestination() + " because " + src + " does not exist");
                }
                try {
                    IoUtils.copy(src, Path.of(copy.getDestination()));
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to copy " + src + " to " + Path.of(copy.getDestination()), e);
                }
            }
        }
    }

    private void addQuarkusBuildConfig(Model pom, Dependency appArtifactDep) {
        Build build = getOrCreateBuild(pom);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(quarkusCore.getInputBom().getGroupId());
        plugin.setArtifactId("quarkus-maven-plugin");
        plugin.setVersion(quarkusCore.getVersionProperty());
        PluginExecution e = new PluginExecution();
        plugin.addExecution(e);
        e.addGoal("build");
        final Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
        e.setConfiguration(config);
        final StringBuilder sb = new StringBuilder();
        sb.append(appArtifactDep.getGroupId()).append(':').append(appArtifactDep.getArtifactId()).append(':');
        if (appArtifactDep.getClassifier() != null && !appArtifactDep.getClassifier().isEmpty()) {
            sb.append(appArtifactDep.getClassifier()).append(':').append(appArtifactDep.getType()).append(':');
        }
        sb.append(appArtifactDep.getVersion());
        config.addChild(textDomElement("appArtifact", sb.toString()));
    }

    /**
     * Returns either a property expression that should be used in place of the actual artifact version
     * or the actual artifact version, in case no property was found that could represent the version
     * 
     * @param artifactGroupId test artifact groupId
     * @param version test artifact version
     * @return property expression or the actual version
     */
    private String getTestArtifactVersion(String artifactGroupId, String version) {
        if (pomPropsByValues.isEmpty()) {
            mapProjectProperties(project.getOriginalModel().getProperties());
            for (Profile p : project.getActiveProfiles()) {
                mapProjectProperties(p.getProperties());
            }
        }
        String versionProp = pomPropsByValues.get(version);
        if (versionProp == null) {
            return version;
        }
        if (versionProp.isEmpty()) {
            versionProp = pomPropsByValues.get(artifactGroupId + COLON + version);
            if (versionProp == null) {
                return version;
            }
        }
        return "${" + versionProp + "}";
    }

    private void mapProjectProperties(Properties props) {
        for (Map.Entry<?, ?> prop : props.entrySet()) {
            final String name = prop.getKey().toString();
            final String value = prop.getValue().toString();
            final String previous = pomPropsByValues.putIfAbsent(value, name);
            if (previous != null) {
                Collection<String> groupIds = getArtifactGroupIdsForVersionProperty(name);
                if (groupIds.isEmpty()) {
                    continue;
                }
                for (var groupId : groupIds) {
                    pomPropsByValues.put(groupId + COLON + value, name);
                }
                if (previous.isEmpty()) {
                    continue;
                }
                groupIds = getArtifactGroupIdsForVersionProperty(previous);
                for (var groupId : groupIds) {
                    pomPropsByValues.put(groupId + COLON + value, previous);
                }
                pomPropsByValues.put(value, "");
            }
        }
    }

    private Collection<String> getArtifactGroupIdsForVersionProperty(final String versionProperty) {
        var propExpr = "${" + versionProperty + "}";
        Set<String> result = null;
        for (String s : pomLines()) {
            int coordsEnd = s.indexOf(propExpr);
            // looking for <p>propExpr</p>, min length will be propExpr.length() + 3 + 4
            if (coordsEnd < 0
                    || s.length() < propExpr.length() + 7) {
                continue;
            }
            coordsEnd = s.indexOf("</", coordsEnd);
            if (coordsEnd < 0) {
                continue;
            }
            int coordsStart = s.indexOf(">");
            if (coordsStart < 0) {
                continue;
            }
            var coords = s.substring(coordsStart + 1, coordsEnd);
            var arr = coords.split(COLON);
            if (arr.length > 2 && arr.length < 6) {
                if (result == null) {
                    result = new HashSet<>(2);
                }
                result.add(arr[0]);
            }
        }
        return result == null ? Set.of() : result;
    }

    private void addDependencies(final Model pom, List<String> dependencies, boolean test) {
        if (dependencies.isEmpty()) {
            return;
        }
        var existingDeps = new HashMap<>(pom.getDependencies().size());
        for (var d : pom.getDependencies()) {
            existingDeps.put(ArtifactKey.of(d.getGroupId(), d.getArtifactId(),
                    d.getClassifier() == null ? ArtifactCoords.DEFAULT_CLASSIFIER : d.getClassifier(),
                    d.getType() == null ? ArtifactCoords.TYPE_JAR : d.getType()), d);
        }
        for (String depStr : dependencies) {
            final ArtifactCoords coords = ArtifactCoords.fromString(depStr);
            if (existingDeps.containsKey(coords.getKey())) {
                continue;
            }
            final Dependency dep = new Dependency();
            dep.setGroupId(coords.getGroupId());
            dep.setArtifactId(coords.getArtifactId());
            if (!coords.getClassifier().isEmpty()) {
                dep.setClassifier(coords.getClassifier());
            }
            if (!ArtifactCoords.TYPE_JAR.equals(coords.getType())) {
                dep.setType(coords.getType());
            }
            if (!universalBomDepKeys.containsKey(ArtifactKey.of(coords.getGroupId(), coords.getArtifactId(),
                    coords.getClassifier(), coords.getType()))) {
                dep.setVersion(coords.getVersion());
            }
            if (test) {
                dep.setScope(TEST);
            }
            pom.addDependency(dep);
        }
    }

    private void addGroupsConfig(PlatformMemberTestConfig testConfig, Xpp3Dom config, boolean nativeTest) {
        String groupsStr = testConfig.getGroups();
        if (nativeTest && testConfig.getNativeGroups() != null) {
            groupsStr = testConfig.getNativeGroups();
        }
        if (groupsStr == null) {
            return;
        }
        config.addChild(textDomElement("groups", groupsStr));
    }

    private static void addIncludesExcludesConfig(PlatformMemberTestConfig testConfig, Xpp3Dom config, boolean nativeTest) {
        if (nativeTest) {
            if (!testConfig.getNativeIncludes().isEmpty()) {
                addElements(config, INCLUDES, INCLUDE, testConfig.getNativeIncludes());
            }
            if (!testConfig.getNativeExcludes().isEmpty()) {
                addElements(config, EXCLUDES, EXCLUDE, testConfig.getNativeExcludes());
            }
        } else {
            if (!testConfig.getJvmIncludes().isEmpty()) {
                addElements(config, INCLUDES, INCLUDE, testConfig.getJvmIncludes());
            }
            if (!testConfig.getJvmExcludes().isEmpty()) {
                addElements(config, EXCLUDES, EXCLUDE, testConfig.getJvmExcludes());
            }
        }
    }

    private static void addElements(Xpp3Dom config, String wrapperName, String elementName, List<String> values) {
        final Xpp3Dom includes = new Xpp3Dom(wrapperName);
        config.addChild(includes);
        for (String s : values) {
            includes.addChild(textDomElement(elementName, s));
        }
    }

    private TransformerFactory getTransformerFactory() throws TransformerFactoryConfigurationError {
        return transformerFactory == null ? transformerFactory = TransformerFactory.newInstance() : transformerFactory;
    }

    private Plugin createFailsafeConfig(PlatformMemberTestConfig testConfig, final Xpp3Dom depsToScan, boolean nativeTest) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-failsafe-plugin");

        Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
        plugin.setConfiguration(config);
        config.addChild(depsToScan);

        plugin.setConfiguration(config);
        PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.addGoal("integration-test");
        exec.addGoal("verify");

        config = new Xpp3Dom(CONFIGURATION);
        exec.setConfiguration(config);
        if (nativeTest) {
            final Xpp3Dom nativeImagePath = new Xpp3Dom("native.image.path");
            getOrCreateChild(config, SYSTEM_PROPERTY_VARIABLES).addChild(nativeImagePath);
            nativeImagePath.setValue("${project.build.directory}/${project.build.finalName}-runner");
        }
        if (!testConfig.getSystemProperties().isEmpty()) {
            addMapConfig(getOrCreateChild(config, SYSTEM_PROPERTY_VARIABLES), testConfig.getSystemProperties());
        }
        if (!testConfig.getEnvironmentVariables().isEmpty()) {
            addMapConfig(getOrCreateChild(config, ENVIRONMENT_VARIABLES), testConfig.getEnvironmentVariables());
        }

        if (nativeTest) {
            if (!testConfig.getNativeSystemProperties().isEmpty()) {
                addMapConfig(getOrCreateChild(config, SYSTEM_PROPERTY_VARIABLES), testConfig.getNativeSystemProperties());
            }
        } else if (!testConfig.getJvmSystemProperties().isEmpty()) {
            addMapConfig(getOrCreateChild(config, SYSTEM_PROPERTY_VARIABLES), testConfig.getJvmSystemProperties());
        }

        if (nativeTest) {
            if (!testConfig.getNativeEnvironmentVariables().isEmpty()) {
                addMapConfig(getOrCreateChild(config, ENVIRONMENT_VARIABLES), testConfig.getNativeEnvironmentVariables());
            }
        } else if (!testConfig.getJvmEnvironmentVariables().isEmpty()) {
            addMapConfig(getOrCreateChild(config, ENVIRONMENT_VARIABLES), testConfig.getJvmEnvironmentVariables());
        }

        if (nativeTest) {
            if (testConfig.getNativeArgLine() != null) {
                config.addChild(textDomElement(ARG_LINE, testConfig.getNativeArgLine()));
            } else if (testConfig.getArgLine() != null) {
                config.addChild(textDomElement(ARG_LINE, testConfig.getArgLine()));
            }
        } else if (testConfig.getJvmArgLine() != null) {
            config.addChild(textDomElement(ARG_LINE, testConfig.getJvmArgLine()));
        } else if (testConfig.getArgLine() != null) {
            config.addChild(textDomElement(ARG_LINE, testConfig.getArgLine()));
        }

        if (nativeTest) {
            if (testConfig.getNativeTestPattern() != null) {
                config.addChild(textDomElement(TEST, testConfig.getNativeTestPattern()));
            } else if (testConfig.getTestPattern() != null) {
                config.addChild(textDomElement(TEST, testConfig.getTestPattern()));
            }
        } else if (testConfig.getJvmTestPattern() != null) {
            config.addChild(textDomElement(TEST, testConfig.getJvmTestPattern()));
        } else if (testConfig.getTestPattern() != null) {
            config.addChild(textDomElement(TEST, testConfig.getTestPattern()));
        }

        addGroupsConfig(testConfig, config, nativeTest);
        addIncludesExcludesConfig(testConfig, config, nativeTest);
        return plugin;
    }

    private static Xpp3Dom getOrCreateChild(Xpp3Dom parent, String child) {
        Xpp3Dom e = parent.getChild(child);
        if (e == null) {
            e = new Xpp3Dom(child);
            parent.addChild(e);
        }
        return e;
    }

    private void addMapConfig(Xpp3Dom sysProps, Map<String, String> props) {
        for (Map.Entry<String, String> entry : props.entrySet()) {
            sysProps.addChild(textDomElement(entry.getKey(), entry.getValue()));
        }
    }

    private void generateUniversalPlatformModule(Model parentPom) throws MojoExecutionException {
        final Artifact bomArtifact = getUniversalBomArtifact();
        final String moduleName = getArtifactIdBase(bomArtifact.getArtifactId());

        final Model pom = newModel();
        pom.setArtifactId(moduleName + "-parent");
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(moduleName) + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);
        setParent(pom, parentPom);

        generatePlatformDescriptorModule(
                ArtifactCoords.of(bomArtifact.getGroupId(),
                        PlatformArtifacts.ensureCatalogArtifactId(bomArtifact.getArtifactId()),
                        bomArtifact.getVersion(), "json", bomArtifact.getVersion()),
                pom, true, null, null);

        // to make the descriptor pom resolvable during the platform BOM generation, we need to persist the generated POMs
        persistPom(pom);
        persistPom(parentPom);
        var generatedBom = generateUniversalPlatformBomModule(pom);

        if (platformConfig.getUniversal().isGeneratePlatformProperties()) {
            final PlatformMemberConfig tmpConfig = new PlatformMemberConfig();
            tmpConfig.setBom(platformConfig.getUniversal().getBom());
            final PlatformMemberImpl tmp = new PlatformMemberImpl(tmpConfig);
            tmp.setAlignedDecomposedBom(generatedBom);
            tmp.baseModel = pom;
            generatePlatformPropertiesModule(tmp, false);
        }

        if (platformConfig.getUniversal().isSkipInstall()) {
            Utils.skipInstallAndDeploy(pom);
        }
        persistPom(pom);
    }

    private void generatePlatformDescriptorModule(ArtifactCoords descriptorCoords, Model parentPom,
            boolean copyQuarkusCoreMetadata, AttachedMavenPluginConfig attachedPlugin, PlatformMember member)
            throws MojoExecutionException {

        var moduleName = "descriptor";
        parentPom.addModule(moduleName);

        final Path moduleDir = parentPom.getProjectDirectory().toPath().resolve(moduleName);

        final Model pom = newModel();

        if (!descriptorCoords.getGroupId().equals(ModelUtils.getGroupId(parentPom))) {
            pom.setGroupId(descriptorCoords.getGroupId());
        }
        pom.setArtifactId(descriptorCoords.getArtifactId());
        if (!descriptorCoords.getVersion().equals(ModelUtils.getVersion(parentPom))) {
            pom.setVersion(descriptorCoords.getVersion());
        }
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " Quarkus Platform Descriptor");

        final Path pomXml = moduleDir.resolve(POM_XML);
        pom.setPomFile(pomXml.toFile());
        setParent(pom, parentPom);

        addResourcesPlugin(pom, true);
        final Build build = pom.getBuild();
        final Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        final PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("process-resources");
        exec.addGoal("generate-platform-descriptor");

        final Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
        final String bomArtifact = PlatformArtifacts.ensureBomArtifactId(descriptorCoords.getArtifactId());
        config.addChild(textDomElement("bomArtifactId", bomArtifact));

        config.addChild(textDomElement("quarkusCoreVersion", quarkusCore.getVersionProperty()));
        if (platformConfig.hasUpstreamQuarkusCoreVersion()) {
            config.addChild(textDomElement("upstreamQuarkusCoreVersion", platformConfig.getUpstreamQuarkusCoreVersion()));
        }

        final Xpp3Dom stackConfig = new Xpp3Dom("platformRelease");
        config.addChild(stackConfig);
        final Xpp3Dom platformKey = new Xpp3Dom("platformKey");
        stackConfig.addChild(platformKey);
        stackConfig.addChild(textDomElement("stream", "${" + PLATFORM_STREAM_PROP + "}"));
        stackConfig.addChild(textDomElement("version", "${" + PLATFORM_RELEASE_PROP + "}"));
        final Xpp3Dom membersConfig = new Xpp3Dom("members");
        stackConfig.addChild(membersConfig);
        if (descriptorCoords.getGroupId().equals(getUniversalBomArtifact().getGroupId())
                && descriptorCoords.getArtifactId()
                        .equals(PlatformArtifacts.ensureCatalogArtifactId(getUniversalBomArtifact().getArtifactId()))) {
            platformKey.setValue("${project.groupId}");
            addMemberDescriptorConfig(pom, membersConfig, descriptorCoords);
        } else {
            platformKey.setValue("${" + PLATFORM_KEY_PROP + "}");
            for (PlatformMember m : members.values()) {
                if (!m.config().isHidden()) {
                    addMemberDescriptorConfig(pom, membersConfig, m.descriptorCoords());
                }
            }
        }

        ObjectNode overrides = null;
        if (copyQuarkusCoreMetadata) {
            // copy the quarkus-bom metadata
            overrides = CatalogMapperHelper.mapper().createObjectNode();
            final Artifact bom = quarkusCore.getInputBom();
            var jsonArtifact = new DefaultArtifact(bom.getGroupId(),
                    bom.getArtifactId() + Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX, bom.getVersion(), "json",
                    bom.getVersion());
            final Path jsonPath;
            try {
                jsonPath = getNonWorkspaceResolver().resolve(jsonArtifact).getArtifact().getFile().toPath();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to resolve " + jsonArtifact, e);
            }
            final JsonNode descriptorNode;
            try (BufferedReader reader = Files.newBufferedReader(jsonPath)) {
                descriptorNode = CatalogMapperHelper.mapper().readTree(reader);
            } catch (IOException e1) {
                throw new MojoExecutionException("Failed to deserialize " + jsonPath, e1);
            }
            final JsonNode metadata = descriptorNode.get("metadata");
            if (metadata != null) {
                if (attachedPlugin != null) {
                    JsonNode props = metadata.get("project");
                    if (props != null) {
                        props = props.get("properties");
                        if (props != null) {
                            final ObjectNode jo = (ObjectNode) props;
                            final ArtifactCoords pluginCoords = ArtifactCoords
                                    .fromString(attachedPlugin.getTargetPluginCoords());
                            final String pluginGroupId = pluginCoords.getGroupId().equals(ModelUtils.getGroupId(pom))
                                    ? "${project.groupId}"
                                    : pluginCoords.getGroupId();
                            jo.replace("maven-plugin-groupId", jo.textNode(pluginGroupId));
                            jo.replace("maven-plugin-version",
                                    jo.textNode(getDependencyVersion(pom, pluginCoords)));
                        }
                    }
                }
                overrides.set("metadata", metadata);
            }
            final JsonNode categories = descriptorNode.get("categories");
            if (categories != null) {
                overrides.set("categories", categories);
            }
        }

        final PlatformDescriptorGeneratorConfig descrGen = platformConfig.getDescriptorGenerator();

        if (member != null) {
            // Update last-bom-update
            var lastUpdatedBom = member.latestBomRelease();
            pom.getProperties().setProperty(MEMBER_LAST_BOM_UPDATE_PROP, lastUpdatedBom.getGroupId() + COLON
                    + lastUpdatedBom.getArtifactId() + COLON + lastUpdatedBom.getVersion());
            if (overrides == null) {
                overrides = CatalogMapperHelper.mapper().createObjectNode();
            }
            JsonNode metadata = overrides.get("metadata");
            if (metadata == null) {
                metadata = overrides.putObject("metadata");
            }
            final ObjectNode on = (ObjectNode) metadata;
            on.set(LAST_BOM_UPDATE, on.textNode("${" + MEMBER_LAST_BOM_UPDATE_PROP + "}"));

            final List<String> extensionGroupIds = member.getExtensionGroupIds();
            if (!extensionGroupIds.isEmpty()) {
                final Xpp3Dom processGroupIds = new Xpp3Dom("processGroupIds");
                config.addChild(processGroupIds);
                for (String groupId : extensionGroupIds) {
                    processGroupIds.addChild(textDomElement("groupId", groupId));
                }
            }

            addExtensionDependencyCheck(member.config().getRedHatExtensionDependencyCheck(), config);
        } else {
            addExtensionDependencyCheck(platformConfig.getUniversal().getRedHatExtensionDependencyCheck(), config);
        }

        if (descrGen != null) {
            if (!descrGen.ignoredArtifacts.isEmpty()) {
                final Xpp3Dom ignoredArtifacts = new Xpp3Dom("ignoredArtifacts");
                config.addChild(ignoredArtifacts);
                for (String coords : descrGen.ignoredArtifacts) {
                    ignoredArtifacts.addChild(textDomElement("artifact", coords));
                }
            }
        }

        // METADATA OVERRIDES
        final StringJoiner metadataOverrideFiles = new StringJoiner(",");
        if (overrides != null && !overrides.isEmpty()) {
            Path overridesFile = moduleDir.resolve("src").resolve("main").resolve("resources").resolve("overrides.json");
            try {
                CatalogMapperHelper.serialize(overrides, overridesFile);
            } catch (Exception ex) {
                throw new MojoExecutionException("Failed to serialize metadata to " + overridesFile, ex);
            }
            overridesFile = moduleDir.resolve("target").resolve("classes").resolve(overridesFile.getFileName());
            metadataOverrideFiles.add("${project.basedir}/" + moduleDir.relativize(overridesFile));
        }

        if (descrGen != null && descrGen.overridesFile != null) {
            for (String path : descrGen.overridesFile.split(",")) {
                metadataOverrideFiles.add("${project.basedir}/" + moduleDir.relativize(Paths.get(path.trim())));
            }
        }

        if (member == null) {
            final List<String> overrideArtifacts = new ArrayList<>(0);
            for (PlatformMember m : members.values()) {
                for (String s : m.config().getMetadataOverrideFiles()) {
                    addMetadataOverrideFile(metadataOverrideFiles, moduleDir, Path.of(s));
                }
                overrideArtifacts.addAll(m.config().getMetadataOverrideArtifacts());
            }
            addMetadataOverrideArtifacts(config, overrideArtifacts);
        } else {
            for (String s : member.config().getMetadataOverrideFiles()) {
                addMetadataOverrideFile(metadataOverrideFiles, moduleDir, Path.of(s));
            }
            addMetadataOverrideArtifacts(config, member.config().getMetadataOverrideArtifacts());
        }

        if (metadataOverrideFiles.length() > 0) {
            config.addChild(textDomElement("overridesFile", metadataOverrideFiles.toString()));
        }

        if (descrGen != null) {
            if (descrGen.skipCategoryCheck) {
                config.addChild(textDomElement("skipCategoryCheck", "true"));
            }
            if (descrGen.resolveDependencyManagement) {
                config.addChild(textDomElement("resolveDependencyManagement", "true"));
            }
        }
        plugin.setConfiguration(config);

        final Dependency dep = new Dependency();
        dep.setGroupId(descriptorCoords.getGroupId());
        dep.setArtifactId(bomArtifact);
        dep.setType(ArtifactCoords.TYPE_POM);
        dep.setVersion(getDependencyVersion(pom, descriptorCoords));
        pom.addDependency(dep);

        if (member != null && member.config().isHidden()) {
            Utils.skipInstallAndDeploy(pom);
        }

        configureFlattenPluginForMetadataArtifacts(pom);
        persistPom(pom);
    }

    private void configureFlattenPluginForMetadataArtifacts(final Model pom) {
        configureFlattenPlugin(pom, true, Map.of(
                "dependencyManagement", "remove",
                "dependencies", "remove",
                "mailingLists", "remove"));
    }

    private void addExtensionDependencyCheck(final RedHatExtensionDependencyCheck depCheckConfig, Xpp3Dom config) {
        if (depCheckConfig != null && depCheckConfig.isEnabled() && depCheckConfig.getVersionPattern() != null) {
            final Xpp3Dom depCheck = new Xpp3Dom("extensionDependencyCheck");
            config.addChild(depCheck);
            depCheck.addChild(textDomElement("versionPattern", depCheckConfig.getVersionPattern()));
            if (depCheckConfig.getCheckDepth() != Integer.MAX_VALUE) {
                depCheck.addChild(textDomElement("checkDepth", String.valueOf(depCheckConfig.getCheckDepth())));
            }
        }
    }

    private void addMetadataOverrideArtifacts(final Xpp3Dom config, final List<String> overrideArtifacts) {
        if (overrideArtifacts.isEmpty()) {
            return;
        }
        final Xpp3Dom artifacts = new Xpp3Dom("metadataOverrideArtifacts");
        config.addChild(artifacts);
        for (String s : overrideArtifacts) {
            artifacts.addChild(textDomElement("artifact", s));
        }
    }

    private void addMetadataOverrideFile(final StringJoiner metadataOverrideFiles, final Path moduleDir,
            final Path file) throws MojoExecutionException {
        if (!Files.exists(file)) {
            throw new MojoExecutionException("Configured metadata overrides file " + file + " does not exist");
        }
        metadataOverrideFiles.add("${project.basedir}/" + moduleDir.relativize(file));
    }

    private void addMemberDescriptorConfig(final Model pom, final Xpp3Dom membersConfig,
            final ArtifactCoords memberCoords) {
        final String value;
        if (memberCoords.getGroupId().equals(ModelUtils.getGroupId(pom))
                && memberCoords.getVersion().equals(ModelUtils.getVersion(pom))) {
            value = "${project.groupId}:" + memberCoords.getArtifactId() + ":${project.version}:json:${project.version}";
        } else {
            value = memberCoords.toString();
        }
        membersConfig.addChild(textDomElement("member", value));
    }

    private void generatePlatformPropertiesModule(PlatformMemberImpl member,
            boolean addPlatformReleaseConfig)
            throws MojoExecutionException {

        final ArtifactCoords propertiesCoords = member.propertiesCoords();
        final Model parentPom = member.baseModel;

        final String moduleName = "properties";
        parentPom.addModule(moduleName);

        final Path moduleDir = parentPom.getProjectDirectory().toPath().resolve(moduleName);

        final Model pom = newModel();

        if (!propertiesCoords.getGroupId().equals(ModelUtils.getGroupId(parentPom))) {
            pom.setGroupId(propertiesCoords.getGroupId());
        }
        pom.setArtifactId(propertiesCoords.getArtifactId());
        if (!propertiesCoords.getVersion().equals(ModelUtils.getVersion(parentPom))) {
            pom.setVersion(propertiesCoords.getVersion());
        }
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " Quarkus Platform Properties");

        final Path pomXml = moduleDir.resolve(POM_XML);
        pom.setPomFile(pomXml.toFile());
        setParent(pom, parentPom);

        // for the bom validation to work
        final DependencyManagement dm = getOrCreateDependencyManagement(pom);
        final Dependency bom = new Dependency();
        dm.addDependency(bom);
        bom.setGroupId(propertiesCoords.getGroupId());
        bom.setArtifactId(propertiesCoords.getArtifactId().substring(0,
                propertiesCoords.getArtifactId().length() - Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX.length()));
        bom.setVersion(getDependencyVersion(pom, propertiesCoords));
        bom.setType(ArtifactCoords.TYPE_POM);
        bom.setScope("import");

        addResourcesPlugin(pom, true);
        final Build build = pom.getBuild();

        final Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        final PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("process-resources");
        exec.addGoal("platform-properties");

        final OrderedProperties props = new OrderedProperties.OrderedPropertiesBuilder()
                .withOrdering(String.CASE_INSENSITIVE_ORDER).withSuppressDateInComment(true).build();
        if (member.config().getBom() != null) {
            // this is just to copy the core properties to the universal platform
            final PlatformMember srcMember = platformConfig.getUniversal().getBom().equals(member.config().getBom())
                    ? quarkusCore
                    : member;
            List<org.eclipse.aether.graph.Dependency> originalDm;
            try {
                originalDm = getNonWorkspaceResolver().resolveDescriptor(srcMember.getInputBom()).getManagedDependencies();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to resolve " + member.getInputBom(), e);
            }
            final Properties tmp = new Properties();
            for (org.eclipse.aether.graph.Dependency d : originalDm) {
                final Artifact a = d.getArtifact();
                if (a.getExtension().equals("properties")
                        && a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                        && a.getArtifactId().startsWith(srcMember.getInputBom().getArtifactId())
                        && a.getGroupId().equals(srcMember.getInputBom().getGroupId())
                        && a.getVersion().equals(srcMember.getInputBom().getVersion())) {
                    try (BufferedReader reader = Files
                            .newBufferedReader(nonWsResolver.resolve(a).getArtifact().getFile().toPath())) {
                        tmp.load(reader);
                    } catch (Exception e) {
                        throw new MojoExecutionException("Failed to resolve " + a, e);
                    }
                    break;
                }
            }

            for (Map.Entry<?, ?> prop : tmp.entrySet()) {
                final String name = prop.getKey().toString();
                pom.getProperties().setProperty(name, prop.getValue().toString());
                props.setProperty(name, "${" + name + "}");
            }
        }

        if (addPlatformReleaseConfig) {
            final Xpp3Dom config = new Xpp3Dom(CONFIGURATION);
            final Xpp3Dom stackConfig = new Xpp3Dom("platformRelease");
            config.addChild(stackConfig);
            stackConfig.addChild(textDomElement("platformKey", "${" + PLATFORM_KEY_PROP + "}"));
            stackConfig.addChild(textDomElement("stream", "${" + PLATFORM_STREAM_PROP + "}"));
            stackConfig.addChild(textDomElement("version", "${" + PLATFORM_RELEASE_PROP + "}"));
            final Xpp3Dom membersConfig = new Xpp3Dom("members");
            stackConfig.addChild(membersConfig);
            final Iterator<PlatformMemberImpl> i = members.values().iterator();
            final StringBuilder buf = new StringBuilder();
            while (i.hasNext()) {
                final PlatformMember m = i.next();
                if (m.config().isHidden()) {
                    continue;
                }
                if (buf.length() > 0) {
                    buf.append(",");
                }
                membersConfig.addChild(textDomElement("member", m.descriptorCoords().toString()));
                final ArtifactCoords bomCoords = PlatformArtifacts.ensureBomArtifact(m.descriptorCoords());
                if (bomCoords.getGroupId().equals(project.getGroupId())
                        && bomCoords.getVersion().equals(project.getVersion())) {
                    buf.append("${project.groupId}:").append(bomCoords.getArtifactId()).append("::pom:${project.version}");
                } else {
                    buf.append(bomCoords);
                }
            }

            props.setProperty(
                    "platform.release-info@${" + PLATFORM_KEY_PROP + "}$${"
                            + PLATFORM_STREAM_PROP + "}#${" + PLATFORM_RELEASE_PROP + "}",
                    buf.toString());
        }

        if (member.config().isHidden()) {
            Utils.skipInstallAndDeploy(pom);
        }

        configureFlattenPluginForMetadataArtifacts(pom);

        persistPom(pom);

        final Path dir = pom.getPomFile().toPath().getParent().resolve("src").resolve("main").resolve("resources");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directory " + dir, e);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("platform-properties.properties"))) {
            props.store(writer, pom.getName());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist " + dir.resolve("platform-properties.properties"), e);
        }
    }

    private void addResourcesPlugin(Model pom, boolean filtering) {
        Build build = getOrCreateBuild(pom);
        final Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-resources-plugin");
        final PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("process-resources");
        exec.addGoal("resources");

        if (filtering) {
            final Resource r = new Resource();
            r.setDirectory("src/main/resources");
            r.setFiltering(true);
            build.setResources(Collections.singletonList(r));
        }
    }

    private PluginDescriptor pluginDescriptor() {
        return pluginDescr == null ? pluginDescr = (PluginDescriptor) getPluginContext().get("pluginDescriptor") : pluginDescr;
    }

    private DecomposedBom generateUniversalPlatformBomModule(Model parentPom) throws MojoExecutionException {

        final Artifact bomArtifact = getUniversalBomArtifact();
        final PlatformBomGeneratorConfig bomGen = platformConfig.getBomGenerator();
        final PlatformBomConfig.Builder configBuilder = PlatformBomConfig.builder()
                .artifactResolver(ArtifactResolverProvider.get(getNonWorkspaceResolver()))
                .pomResolver(PomSource.of(bomArtifact))
                .includePlatformProperties(platformConfig.getUniversal().isGeneratePlatformProperties())
                .platformBom(bomArtifact)
                .versionIncrementor(
                        platformConfig.getRelease() == null ? null : platformConfig.getRelease().getVersionIncrementor());

        if (platformConfig.getBomGenerator() != null) {
            configBuilder.disableGroupAlignmentToPreferredVersions(
                    platformConfig.getBomGenerator().disableGroupAlignmentToPreferredVersions);
        }
        for (PlatformMember member : members.values()) {
            configBuilder.addMember(member);
        }

        if (bomGen != null) {
            configBuilder.enableNonMemberQuarkiverseExtensions(bomGen.enableNonMemberQuarkiverseExtensions);
            if (bomGen.enforcedDependencies != null) {
                for (String enforced : bomGen.enforcedDependencies) {
                    final ArtifactCoords coords = ArtifactCoords.fromString(enforced);
                    configBuilder
                            .enforce(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                                    coords.getType(), coords.getVersion()));
                }
            }
            if (bomGen.excludedDependencies != null) {
                for (String excluded : bomGen.excludedDependencies) {
                    configBuilder.exclude(ArtifactKey.fromString(excluded));
                }
            }
            if (bomGen.excludedGroups != null) {
                for (String excluded : bomGen.excludedGroups) {
                    configBuilder.excludeGroupId(excluded);
                }
            }
            configBuilder.versionConstraintPreference(bomGen.versionConstraintPreferences);

            int foreignPreferredConstraint = 0;
            if (bomGen.notPreferredQuarkusBomConstraint != null) {
                if (bomGen.foreignPreferredConstraint != null) {
                    throw new IllegalStateException(
                            "Deprecated notPreferredQuarkusBomConstraint is configured in addition to foreignPreferredConstrait");
                }
                foreignPreferredConstraint = ForeignPreferredConstraint.valueOf(bomGen.notPreferredQuarkusBomConstraint).flag();
            } else if (bomGen.foreignPreferredConstraint != null) {
                for (String s : bomGen.foreignPreferredConstraint.split("\\s*,\\s*")) {
                    if (!s.isEmpty()) {
                        foreignPreferredConstraint |= ForeignPreferredConstraint.valueOf(s).flag();
                    }
                }
            }
            if (foreignPreferredConstraint > 0) {
                configBuilder.foreignPreferredConstraint(foreignPreferredConstraint);
            }
        }

        try {
            universalGeneratedBom = new PlatformBomComposer(configBuilder.build(), new MojoMessageWriter(getLog()))
                    .platformBom();
        } catch (BomDecomposerException e) {
            throw new MojoExecutionException("Failed to generate the platform BOM", e);
        }

        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(parentPom) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        parentPom.addModule(moduleName);
        universalPlatformBomXml = parentPom.getProjectDirectory().toPath().resolve(moduleName).resolve(POM_XML);

        final Model pom = PlatformBomUtils.toPlatformModel(universalGeneratedBom, baseModel, catalogResolver());

        addReleaseProfile(pom);
        try {
            Files.createDirectories(universalPlatformBomXml.getParent());
            ModelUtils.persistModel(universalPlatformBomXml, pom);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist generated BOM to " + universalPlatformBomXml, e);
        }

        for (ProjectRelease r : universalGeneratedBom.releases()) {
            for (ProjectDependency d : r.dependencies()) {
                universalBomDepKeys.put(d.key(), d.artifact().getVersion());
            }
        }
        return universalGeneratedBom;
    }

    private Artifact getUniversalBomArtifact() {
        return universalBom == null ? universalBom = toPomArtifact(platformConfig.getUniversal().getBom()) : universalBom;
    }

    private PlatformCatalogResolver catalogResolver() {
        return catalogs == null ? catalogs = new PlatformCatalogResolver(getWorkspaceAwareMavenResolver()) : catalogs;
    }

    private MavenArtifactResolver getNonWorkspaceResolver() {
        if (nonWsResolver == null) {
            try {
                nonWsResolver = MavenArtifactResolver.builder()
                        .setRepositorySystem(repoSystem)
                        .setRepositorySystemSession(repoSession)
                        .setRemoteRepositoryManager(mvnProvider.getRemoteRepositoryManager())
                        .setRemoteRepositories(repos)
                        .setWorkspaceDiscovery(false)
                        .build();
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
            }
        }
        return nonWsResolver;
    }

    private MavenArtifactResolver getWorkspaceAwareMavenResolver() {
        if (wsAwareResolver == null) {
            wsAwareResolver = mvnProvider.createArtifactResolver(
                    BootstrapMavenContext.config()
                            .setRemoteRepositories(repos)
                            .setCurrentProject(new File(outputDir, POM_XML).toString()));
        }
        return wsAwareResolver;
    }

    private PlatformReleaseConfig releaseConfig() {
        if (platformReleaseConfig == null) {
            final PlatformReleaseConfig tmp = platformConfig.getRelease() == null ? new PlatformReleaseConfig()
                    : platformConfig.getRelease();
            if (tmp.getPlatformKey() == null) {
                tmp.setPlatformKey(project.getGroupId());
            }
            if (tmp.getStream() == null) {
                final String projectVersion = project.getVersion();
                final String[] versionSegments = projectVersion.split("\\.");
                if (versionSegments.length == 0) {
                    tmp.setStream(projectVersion);
                } else if (versionSegments.length < 3) {
                    tmp.setStream(versionSegments[0]);
                } else {
                    tmp.setStream(versionSegments[0] + "." + versionSegments[1]);
                }
            }
            if (tmp.getVersion() == null) {
                tmp.setVersion(quarkusCore.getGeneratedPlatformBom().getVersion());
            }
            platformReleaseConfig = tmp;
        }
        return platformReleaseConfig;
    }

    private class PlatformMemberImpl implements PlatformMember {

        final PlatformMemberConfig config;
        private final Artifact originalBomCoords;
        private Artifact configuredPlatformBom;
        private ArtifactCoords descriptorCoords;
        private ArtifactCoords propertiesCoords;
        private ArtifactKey key;
        private Model baseModel;
        private DecomposedBom originalBom;
        private DecomposedBom generatedBom;
        private Model generatedBomModel;
        private Path generatedPomFile;
        private String versionProperty;
        private Artifact prevBomRelease;
        private Boolean bomChanged;
        private List<org.eclipse.aether.graph.Dependency> inputConstraints;

        PlatformMemberImpl(PlatformMemberConfig config) {
            this.config = config;
            originalBomCoords = config.getBom() == null ? null : toPomArtifact(config.getBom());
        }

        @Override
        public PlatformMemberConfig config() {
            return config;
        }

        @Override
        public List<String> getExtensionGroupIds() {
            if (!config.getExtensionGroupIds().isEmpty()) {
                return config.getExtensionGroupIds();
            }
            if (getInputBom() != null) {
                return List.of(getInputBom().getGroupId());
            }
            return List.of();
        }

        @Override
        public List<String> getOwnGroupIds() {
            if (!config.getOwnGroupIds().isEmpty()) {
                return config.getOwnGroupIds();
            }
            return getExtensionGroupIds();
        }

        @Override
        public Artifact previousLastUpdatedBom() {
            if (prevBomRelease == null) {
                final String prev = config.getRelease() == null ? null : config.getRelease().getLastDetectedBomUpdate();
                if (prev != null) {
                    prevBomRelease = toPomArtifact(prev);
                }
            }
            return prevBomRelease;
        }

        @Override
        public Artifact latestBomRelease() {
            return bomChanged != null && bomChanged || previousLastUpdatedBom() == null ? getGeneratedPlatformBom()
                    : previousLastUpdatedBom();
        }

        @Override
        public Artifact getInputBom() {
            return originalBomCoords;
        }

        @Override
        public Artifact getConfiguredPlatformBom() {
            return configuredPlatformBom == null
                    ? configuredPlatformBom = toPomArtifact(config.getPlatformBom(getUniversalBomArtifact().getGroupId()))
                    : configuredPlatformBom;
        }

        @Override
        public Artifact getGeneratedPlatformBom() {
            return this.getAlignedDecomposedBom().bomArtifact();
        }

        @Override
        public boolean isIncrementBomVersionOnChange() {
            return platformConfig.getRelease() != null
                    && platformConfig.getRelease().isOnlyChangedMembers();
        }

        @Override
        public ArtifactKey key() {
            if (key == null) {
                key = getInputBom() == null ? getGaKey(getConfiguredPlatformBom()) : getGaKey(getInputBom());
            }
            return key;
        }

        @Override
        public List<org.eclipse.aether.graph.Dependency> inputConstraints() {
            if (inputConstraints == null) {
                final List<org.eclipse.aether.graph.Dependency> dm = config.getDependencyManagement().toAetherDependencies();
                if (getInputBom() == null) {
                    inputConstraints = dm;
                } else if (dm.isEmpty()) {
                    inputConstraints = List.of(new org.eclipse.aether.graph.Dependency(getInputBom(), "import"));
                } else {
                    dm.add(new org.eclipse.aether.graph.Dependency(getInputBom(), "import"));
                    inputConstraints = dm;
                }
            }
            return inputConstraints;
        }

        @Override
        public ArtifactCoords descriptorCoords() {
            return descriptorCoords == null
                    ? descriptorCoords = ArtifactCoords.of(getGeneratedPlatformBom().getGroupId(),
                            getGeneratedPlatformBom().getArtifactId()
                                    + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                            getGeneratedPlatformBom().getVersion(), "json", getGeneratedPlatformBom().getVersion())
                    : descriptorCoords;
        }

        @Override
        public ArtifactCoords propertiesCoords() {
            return propertiesCoords == null
                    ? propertiesCoords = ArtifactCoords.of(getGeneratedPlatformBom().getGroupId(),
                            getGeneratedPlatformBom().getArtifactId()
                                    + BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX,
                            null, "properties", getGeneratedPlatformBom().getVersion())
                    : propertiesCoords;
        }

        @Override
        public String getVersionProperty() {
            if (versionProperty == null) {
                final Artifact quarkusBom = quarkusCore.getInputBom();
                versionProperty = getTestArtifactVersion(quarkusBom.getGroupId(), quarkusBom.getVersion());
            }
            return versionProperty;
        }

        @Override
        public DecomposedBom originalDecomposedBom() {
            return originalBom;
        }

        @Override
        public void setOriginalDecomposedBom(DecomposedBom originalBom) {
            this.originalBom = originalBom;
        }

        @Override
        public void setAlignedDecomposedBom(DecomposedBom alignedBom) {
            this.generatedBom = alignedBom;
        }

        @Override
        public DecomposedBom getAlignedDecomposedBom() {
            return generatedBom;
        }

        @Override
        public Collection<ArtifactKey> extensionCatalog() {
            return List.of();
        }

        @Override
        public void setExtensionCatalog(Collection<ArtifactKey> extensionCatalog) {
        }
    }

    private static Xpp3Dom getOrCreateConfiguration(Plugin plugin) {
        Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
        if (config == null) {
            config = new Xpp3Dom(CONFIGURATION);
            plugin.setConfiguration(config);
        }
        return config;
    }

    private static Plugin getOrCreatePlugin(Build build, String groupId, String artifactId) {
        for (var plugin : build.getPlugins()) {
            if (plugin.getArtifactId().equals(artifactId)
                    && (plugin.getGroupId() == null || groupId.equals(plugin.getGroupId()))) {
                return plugin;
            }
        }
        final Plugin plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(artifactId);
        build.addPlugin(plugin);
        return plugin;
    }

    private static DependencyManagement getOrCreateDependencyManagement(Model pom) {
        DependencyManagement dm = pom.getDependencyManagement();
        if (dm == null) {
            dm = new DependencyManagement();
            pom.setDependencyManagement(dm);
        }
        return dm;
    }

    private static void setParent(Model pom, Model parentPom) {
        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setRelativePath(
                pom.getPomFile().toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);
        setParentVersion(pom, parentPom);
    }

    private static Build getOrCreateBuild(Model pom) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        return build;
    }

    private static Build getOrCreateBuild(Profile profile) {
        Build build = (Build) profile.getBuild();
        if (build == null) {
            build = new Build();
            profile.setBuild(build);
        }
        return build;
    }

    private static String getDependencyVersion(Model pom, ArtifactCoords coords) {
        return ModelUtils.getRawVersion(pom).equals(coords.getVersion()) ? "${project.version}" : coords.getVersion();
    }

    private static ArtifactKey getGaKey(Artifact a) {
        return ArtifactKey.ga(a.getGroupId(), a.getArtifactId());
    }

    private static ArtifactKey getKey(Dependency d) {
        return ArtifactKey.of(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType());
    }

    private static DefaultArtifact toPomArtifact(String coords) {
        return toPomArtifact(ArtifactCoords.fromString(coords));
    }

    private static DefaultArtifact toPomArtifact(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), ArtifactCoords.DEFAULT_CLASSIFIER,
                ArtifactCoords.TYPE_POM, coords.getVersion());
    }

    private List<org.eclipse.aether.graph.Dependency> toAetherDependencies(List<Dependency> deps) {
        final List<org.eclipse.aether.graph.Dependency> result = new ArrayList<>(deps.size());
        var atr = getNonWorkspaceResolver().getSession().getArtifactTypeRegistry();
        for (var d : deps) {
            result.add(RepositoryUtils.toDependency(d, atr));
        }
        return result;
    }

    private static Artifact toAetherArtifact(String coords) {
        final ArtifactCoords a = ArtifactCoords.fromString(coords);
        return new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getType(), a.getVersion());
    }

    private static void persistPom(final Model pom) throws MojoExecutionException {
        try {
            pom.getPomFile().getParentFile().mkdirs();
            ModelUtils.persistModel(pom.getPomFile().toPath(), pom);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate the platform BOM " + pom.getPomFile(), e);
        }
    }

    private static String getArtifactIdBase(Model pom) {
        return getArtifactIdBase(pom.getArtifactId());
    }

    private static String getArtifactIdBase(final String s) {
        final int i = s.lastIndexOf('-');
        return i > 0 ? s.substring(0, i) : s;
    }

    private static String getNameBase(Model pom) {
        final String s = pom.getName();
        final int i = s.lastIndexOf('-');
        return i > 0 ? s.substring(0, i + 1) : s;
    }

    private static String artifactIdToName(String artifactId) {
        final String[] parts = artifactId.split("-");
        final StringBuilder buf = new StringBuilder(artifactId.length() + parts.length);
        String part = parts[0];
        buf.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        for (int i = 1; i < parts.length; ++i) {
            part = parts[i];
            buf.append(' ').append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return buf.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Path deleteAndCreateDir(Path dir) throws MojoExecutionException {
        IoUtils.recursiveDelete(dir);
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directory " + dir, e);
        }
    }
}
