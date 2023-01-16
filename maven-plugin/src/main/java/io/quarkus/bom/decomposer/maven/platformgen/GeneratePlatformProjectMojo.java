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
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.registry.Constants;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
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
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

@Mojo(name = "generate-platform-project", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE)
public class GeneratePlatformProjectMojo extends AbstractMojo {

    private static final String LAST_BOM_UPDATE = "last-bom-update";
    private static final String MEMBER_LAST_BOM_UPDATE_PROP = "member.last-bom-update";
    private static final String PLATFORM_KEY_PROP = "platform.key";
    private static final String PLATFORM_STREAM_PROP = "platform.stream";
    private static final String PLATFORM_RELEASE_PROP = "platform.release";

    @Component
    private RepositorySystem repoSystem;

    @Component
    private RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repos;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
    private List<RemoteRepository> pluginRepos;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

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

    Artifact universalBom;
    MavenArtifactResolver nonWorkspaceResolver;
    MavenArtifactResolver mavenResolver;
    ArtifactResolver artifactResolver;

    PlatformCatalogResolver catalogs;
    final Map<ArtifactKey, PlatformMemberImpl> members = new LinkedHashMap<>();

    private PlatformMemberImpl quarkusCore;

    private DecomposedBom universalGeneratedBom;

    private Path universalPlatformBomXml;

    private PluginDescriptor pluginDescr;

    private List<String> pomLines;

    @Parameter(property = "recordUpdatedBoms")
    private boolean recordUpdatedBoms;

    private Set<ArtifactKey> universalBomDepKeys = new HashSet<>();

    private TransformerFactory transformerFactory;

    // POM property names by values
    private Map<String, String> pomPropsByValues = new HashMap<>();

    private Profile generatedBomReleaseProfile;

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

        final File pomXml = new File(outputDir, "pom.xml");
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(project.getGroupId());
        parent.setArtifactId(project.getArtifactId());
        parent.setVersion(project.getVersion());
        parent.setRelativePath(pomXml.toPath().getParent().relativize(project.getFile().getParentFile().toPath()).toString());
        pom.setParent(parent);

        pom.getProperties().setProperty(PLATFORM_KEY_PROP, releaseConfig().getPlatformKey());
        pom.getProperties().setProperty(PLATFORM_STREAM_PROP, releaseConfig().getStream());
        pom.getProperties().setProperty(PLATFORM_RELEASE_PROP, releaseConfig().getVersion());

        final Build build = new Build();
        pom.setBuild(build);
        final PluginManagement pm = new PluginManagement();
        pom.getBuild().setPluginManagement(pm);
        final Plugin plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        plugin.setVersion(getTestArtifactVersion(pluginDescriptor().getGroupId(), pluginDescriptor().getVersion()));
        plugin.setExtensions(true);

        generateUniversalPlatformModule(pom);

        for (PlatformMemberImpl member : members.values()) {
            generateMemberModule(member, pom);
        }

        for (PlatformMemberImpl member : members.values()) {
            generatePlatformDescriptorModule(member.descriptorCoords(), member.baseModel, true,
                    quarkusCore.originalBomCoords().equals(member.originalBomCoords()),
                    platformConfig.getAttachedMavenPlugin(), member);
            generatePlatformPropertiesModule(member, true);
            persistPom(member.baseModel);
        }

        if (platformConfig.getAttachedMavenPlugin() != null) {
            generateMavenPluginModule(pom);
        }

        if (dependenciesToBuild != null) {
            generateDepsToBuildModule(pom);
        }

        if (platformConfig.getGenerateMavenRepoZip() != null) {
            generateMavenRepoZipModule(pom);
        }

        addReleaseProfile(pom);
        persistPom(pom);

        recordUpdatedBoms();

        if (platformConfig.isGenerateBomReports() || platformConfig.getGenerateBomReportsZip() != null) {
            final Path reportsOutputDir = reportsDir.toPath();
            // reset the resolver to pick up all the generated platform modules
            //resetResolver();
            try (ReportIndexPageGenerator index = new ReportIndexPageGenerator(
                    reportsOutputDir.resolve("index.html"))) {

                final Path releasesReport = reportsOutputDir.resolve("main").resolve("generated-releases.html");
                generateReleasesReport(universalGeneratedBom, releasesReport);
                index.universalBom(universalPlatformBomXml.toUri().toURL(), universalGeneratedBom, releasesReport);

                for (PlatformMemberImpl member : members.values()) {
                    if (member.originalBomCoords() == null) {
                        continue;
                    }
                    generateBomReports(member.originalBom, member.generatedBom,
                            reportsOutputDir.resolve(member.config().getName().toLowerCase()), index,
                            member.generatedPomFile, artifactResolver());
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to generate platform member BOM reports", e);
            }

            if (platformConfig.getGenerateBomReportsZip() != null) {
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
            }
        }
    }

    private void generateDepsToBuildModule(Model parentPom) throws MojoExecutionException {
        final Model pom = newModel();
        final String artifactId = "quarkus-dependencies-to-build";
        pom.setArtifactId(artifactId);
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(artifactId));
        parentPom.addModule(artifactId);
        final File pomXml = getPomFile(parentPom, artifactId);
        pom.setPomFile(pomXml);
        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);
        Utils.skipInstallAndDeploy(pom);

        Plugin plugin = new Plugin();
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        Profile profile = new Profile();
        profile.setId("depsToBuild");
        pom.addProfile(profile);
        final Activation activation = new Activation();
        profile.setActivation(activation);
        final ActivationProperty ap = new ActivationProperty();
        activation.setProperty(ap);
        ap.setName("depsToBuild");

        build = new Build();
        profile.setBuild(build);

        PluginManagement pm = new PluginManagement();
        build.setPluginManagement(pm);
        plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());

        final StringBuilder sb = new StringBuilder();
        for (PlatformMemberImpl m : members.values()) {
            if (!m.config.isHidden() && m.config.isEnabled()) {
                sb.append("quarkus-platform-bom:dependencies-to-build@").append(m.generatedBomCoords().getArtifactId())
                        .append(' ');
            }
        }
        build.setDefaultGoal(sb.toString());

        Path outputDir = buildDir.toPath().resolve("dependencies-to-build");
        final String prefix = pomXml.toPath().getParent().relativize(outputDir).toString();
        for (PlatformMemberImpl m : members.values()) {
            if (m.config.isHidden() || !m.config.isEnabled()) {
                continue;
            }
            final PluginExecution exec = new PluginExecution();
            plugin.addExecution(exec);
            exec.setId(m.generatedBomCoords().getArtifactId());
            exec.setPhase("process-resources");
            exec.addGoal("dependencies-to-build");
            final Xpp3Dom config = new Xpp3Dom("configuration");
            exec.setConfiguration(config);
            config.addChild(textDomElement("bom",
                    m.generatedBomCoords().getGroupId() + ":" + m.generatedBomCoords().getArtifactId() + ":"
                            + getDependencyVersion(pom, m.descriptorCoords())));
            config.addChild(
                    textDomElement("outputFile", prefix + "/" + m.generatedBomCoords().getArtifactId() + "-deps-to-build.txt"));

            final ProjectDependencyFilterConfig depsToBuildConfig = m.config().getDependenciesToBuild();
            if (depsToBuildConfig != null) {
                final Xpp3Dom depsToBuildDom = newDomSelfAppend("dependenciesToBuild");
                config.addChild(depsToBuildDom);
                if (!depsToBuildConfig.getExcludeArtifacts().isEmpty()) {
                    final Xpp3Dom excludeArtifactsDom = newDomChildrenAppend("excludeArtifacts");
                    depsToBuildDom.addChild(excludeArtifactsDom);
                    for (ArtifactCoords artifact : depsToBuildConfig.getExcludeArtifacts()) {
                        final Xpp3Dom artifactDom = newDom("artifact");
                        excludeArtifactsDom.addChild(artifactDom);
                        artifactDom.setValue(artifact.toGACTVString());
                    }
                }
                if (!depsToBuildConfig.getExcludeGroupIds().isEmpty()) {
                    final Xpp3Dom excludeGroupIdsDom = newDomChildrenAppend("excludeGroupIds");
                    depsToBuildDom.addChild(excludeGroupIdsDom);
                    for (String groupId : depsToBuildConfig.getExcludeGroupIds()) {
                        final Xpp3Dom groupIdDom = newDom("groupId");
                        excludeGroupIdsDom.addChild(groupIdDom);
                        groupIdDom.setValue(groupId);
                    }
                }
                if (!depsToBuildConfig.getExcludeKeys().isEmpty()) {
                    final Xpp3Dom excludeKeysDom = newDomChildrenAppend("excludeKeys");
                    depsToBuildDom.addChild(excludeKeysDom);
                    for (ArtifactKey key : depsToBuildConfig.getExcludeKeys()) {
                        final Xpp3Dom keyDom = newDom("key");
                        excludeKeysDom.addChild(keyDom);
                        keyDom.setValue(key.toString());
                    }
                }
                if (!depsToBuildConfig.getIncludeArtifacts().isEmpty()) {
                    final Xpp3Dom includeArtifactsDom = newDomChildrenAppend("includeArtifacts");
                    depsToBuildDom.addChild(includeArtifactsDom);
                    for (ArtifactCoords artifact : depsToBuildConfig.getIncludeArtifacts()) {
                        final Xpp3Dom artifactDom = newDom("artifact");
                        includeArtifactsDom.addChild(artifactDom);
                        artifactDom.setValue(artifact.toGACTVString());
                    }
                }
                if (!depsToBuildConfig.getIncludeGroupIds().isEmpty()) {
                    final Xpp3Dom includeGroupIdsDom = newDomChildrenAppend("includeGroupIds");
                    depsToBuildDom.addChild(includeGroupIdsDom);
                    for (String groupId : depsToBuildConfig.getIncludeGroupIds()) {
                        final Xpp3Dom groupIdDom = newDom("groupId");
                        includeGroupIdsDom.addChild(groupIdDom);
                        groupIdDom.setValue(groupId);
                    }
                }
                if (!depsToBuildConfig.getIncludeKeys().isEmpty()) {
                    final Xpp3Dom includeKeysDom = newDomChildrenAppend("includeKeys");
                    depsToBuildDom.addChild(includeKeysDom);
                    for (ArtifactKey key : depsToBuildConfig.getIncludeKeys()) {
                        final Xpp3Dom keyDom = newDom("key");
                        includeKeysDom.addChild(keyDom);
                        keyDom.setValue(key.toString());
                    }
                }
            }
        }

        persistPom(pom);
    }

    private static void generateReleasesReport(DecomposedBom originalBom, Path outputFile)
            throws BomDecomposerException {
        originalBom.visit(DecomposedBomHtmlReportGenerator.builder(outputFile)
                .skipOriginsWithSingleRelease().build());
    }

    private static void generateBomReports(DecomposedBom originalBom, DecomposedBom generatedBom, Path outputDir,
            ReportIndexPageGenerator index, final Path platformBomXml, ArtifactResolver resolver)
            throws BomDecomposerException {
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
        final Profile releaseProfile = getGeneratedBomReleaseProfile();
        if (releaseProfile != null) {
            pom.addProfile(releaseProfile);
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
        if (!member.bomChanged) {
            return;
        }
        final int memberIndex = pomLineContaining("<name>" + member.config().getName() + "</name>", membersIndex);
        if (memberIndex < 0) {
            throw new MojoExecutionException(
                    "Failed to locate member configuration with <name>" + member.config().getName() + "</name>");
        }
        final int releaseIndex = pomLineContaining("<release>", memberIndex);
        if (releaseIndex < 0) {
            throw new MojoExecutionException("Failed to locate <release> configuration for member with <name>"
                    + member.config().getName() + "</name>");
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
        buf.append("    <lastDetectedBomUpdate>").append(member.generatedBomCoords().getGroupId()).append(":")
                .append(member.generatedBomCoords().getArtifactId()).append(":")
                .append(member.generatedBomCoords().getVersion()).append("</lastDetectedBomUpdate>");
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
            sourcesJar = nonWorkspaceResolver().resolve(new DefaultArtifact(originalCoords.getGroupId(),
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
        try {
            Files.list(javaSources).forEach(p -> {
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
                    mavenDir.resolve(originalCoords.getGroupId()).resolve(originalCoords.getArtifactId()).resolve("pom.xml"),
                    baseDir.resolve("pom.xml"));
            IoUtils.recursiveDelete(mavenDir);
            IoUtils.recursiveDelete(metainfDir.resolve("INDEX.LIST"));
            IoUtils.recursiveDelete(metainfDir.resolve("MANIFEST.MF"));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to import original plugin sources", e);
        }

        // Delete the generated HelpMojo
        try {
            Files.walkFileTree(javaSources, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
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

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        DependencyManagement dm = pom.getDependencyManagement();
        if (dm == null) {
            dm = new DependencyManagement();
            pom.setDependencyManagement(dm);
        }
        final Artifact quarkusBom = quarkusCore.generatedBomCoords();
        final Dependency quarkusBomImport = new Dependency();
        quarkusBomImport.setGroupId(quarkusBom.getGroupId());
        quarkusBomImport.setArtifactId(quarkusBom.getArtifactId());
        quarkusBomImport.setType(ArtifactCoords.TYPE_POM);
        quarkusBomImport.setVersion(quarkusBom.getVersion());
        quarkusBomImport.setScope("import");
        dm.addDependency(quarkusBomImport);

        final List<org.eclipse.aether.graph.Dependency> originalDeps;
        try {
            originalDeps = nonWorkspaceResolver()
                    .resolveDescriptor(new DefaultArtifact(originalCoords.getGroupId(), originalCoords.getArtifactId(),
                            ArtifactCoords.TYPE_JAR, originalCoords.getVersion()))
                    .getDependencies();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve the artifact descriptor of " + originalCoords, e);
        }

        final Map<ArtifactKey, String> originalDepVersions = new HashMap<>(originalDeps.size());
        for (org.eclipse.aether.graph.Dependency d : originalDeps) {
            final Artifact a = d.getArtifact();
            originalDepVersions.put(ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension()),
                    a.getVersion());
        }
        final List<Dependency> managedDeps = quarkusCore.generatedBomModel.getDependencyManagement().getDependencies();
        final Map<ArtifactKey, String> managedDepVersions = new HashMap<>(managedDeps.size());
        for (Dependency d : managedDeps) {
            managedDepVersions.put(getKey(d), d.getVersion());
        }

        final List<Dependency> pluginDeps = pom.getDependencies();
        pom.setDependencies(new ArrayList<>(pluginDeps.size()));
        for (Dependency d : pluginDeps) {
            if ("test".equals(d.getScope())) {
                continue;
            }
            if (d.getVersion() == null) {
                final ArtifactKey key = getKey(d);
                if (!managedDepVersions.containsKey(key)) {
                    final String originalVersion = originalDepVersions.get(key);
                    if (originalVersion == null) {
                        throw new IllegalStateException("Failed to determine version for dependency " + d
                                + " of the Maven plugin " + originalCoords);
                    }
                    d.setVersion(getTestArtifactVersion(originalCoords.getGroupId(), originalVersion));
                }
            } else if (d.getVersion().startsWith("${")) {
                final ArtifactKey key = getKey(d);
                final String originalVersion = originalDepVersions.get(key);
                if (originalVersion == null) {
                    throw new IllegalStateException(
                            "Failed to determine version for dependency " + d + " of the Maven plugin " + originalCoords);
                }
                if (originalVersion.equals(managedDepVersions.get(key))) {
                    d.setVersion(null);
                } else {
                    d.setVersion(getTestArtifactVersion(originalCoords.getGroupId(), originalVersion));
                }
            }
            pom.addDependency(d);
        }

        // make sure the original properties do not override the platform ones
        final Properties originalProps = pom.getProperties();
        if (!originalProps.isEmpty()) {
            pom.setProperties(new Properties());
            for (Map.Entry<?, ?> originalProp : originalProps.entrySet()) {
                final String propName = originalProp.getKey().toString();
                if (getTestArtifactGroupIdForProperty(propName) == null) {
                    pom.getProperties().setProperty(propName, originalProp.getValue().toString());
                }
            }
        }

        if (pluginConfig.isFlattenPom()) {
            configureFlattenPlugin(pom, false, Map.of("dependencyManagement", "keep"));
        }

        persistPom(pom);
    }

    private void configureFlattenPlugin(Model pom, boolean updatePomFile, Map<String, String> elementConfig) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("org.codehaus.mojo");
        plugin.setArtifactId("flatten-maven-plugin");
        PluginExecution e = new PluginExecution();
        plugin.addExecution(e);
        e.setId("flatten");
        e.setPhase("process-resources");
        e.addGoal("flatten");
        Xpp3Dom config = new Xpp3Dom("configuration");
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

    private static Xpp3Dom newDom(String name) {
        return new Xpp3Dom(name);
    }

    private ArtifactKey getKey(Dependency d) {
        return ArtifactKey.of(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType());
    }

    private static File getPomFile(Model parentPom, final String moduleName) {
        return new File(new File(parentPom.getProjectDirectory(), moduleName), "pom.xml");
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

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        DependencyManagement dm = pom.getDependencyManagement();
        if (dm == null) {
            dm = new DependencyManagement();
            pom.setDependencyManagement(dm);
        }
        final Artifact quarkusBom = quarkusCore.generatedBomCoords();
        final Dependency quarkusBomImport = new Dependency();
        quarkusBomImport.setGroupId(quarkusBom.getGroupId());
        quarkusBomImport.setArtifactId(quarkusBom.getArtifactId());
        quarkusBomImport.setType(ArtifactCoords.TYPE_POM);
        quarkusBomImport.setVersion(quarkusBom.getVersion());
        quarkusBomImport.setScope("import");
        dm.addDependency(quarkusBomImport);

        final Build build = new Build();
        pom.setBuild(build);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("generate-resources");
        exec.addGoal("attach-maven-plugin");
        Xpp3Dom config = new Xpp3Dom("configuration");
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
        config = new Xpp3Dom("configuration");
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
        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);
        Utils.skipInstallAndDeploy(pom);

        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
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

        build = new Build();
        profile.setBuild(build);

        final StringBuilder sb = new StringBuilder();
        for (PlatformMemberImpl m : members.values()) {
            if (!m.config.isHidden() && m.config.isEnabled()) {
                sb.append("quarkus-platform-bom:generate-maven-repo-zip@").append(m.generatedBomCoords().getArtifactId())
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
            exec.setId(m.generatedBomCoords().getArtifactId());
            exec.setPhase("process-resources");
            exec.addGoal("generate-maven-repo-zip");

            Xpp3Dom e = new Xpp3Dom("generateMavenRepoZip");

            final Xpp3Dom bom = new Xpp3Dom("bom");
            bom.setValue(m.generatedBomCoords().toString());
            e.addChild(bom);

            if (generateMavenRepoZip.getRepositoryDir() != null) {
                e.addChild(textDomElement("repositoryDir", generateMavenRepoZip.getRepositoryDir()));
            }

            final Path repoDir = repoOutputDir.resolve(m.generatedBomCoords().getArtifactId());
            e.addChild(textDomElement("repositoryDir", repoDir.toString()));
            e.addChild(textDomElement("zipLocation",
                    repoDir + "/" + m.generatedBomCoords().getArtifactId() + "-maven-repo.zip"));

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

            final Xpp3Dom configuration = new Xpp3Dom("configuration");
            configuration.addChild(e);
            exec.setConfiguration(configuration);

        }
        persistPom(pom);
    }

    private void generateMemberModule(PlatformMemberImpl member, Model parentPom) throws MojoExecutionException {

        final String moduleName = getArtifactIdBase(member.generatedBomCoords().getArtifactId());

        final Model pom = newModel();

        if (!member.generatedBomCoords().getGroupId().equals(project.getGroupId())) {
            pom.setGroupId(member.generatedBomCoords().getGroupId());
        }
        pom.setArtifactId(moduleName + "-parent");
        if (!member.generatedBomCoords().getVersion().equals(project.getVersion())) {
            pom.setVersion(member.generatedBomCoords().getVersion());
        }

        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + member.config().getName() + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        member.baseModel = pom;

        generateMemberBom(member);

        if (member.config().hasTests()) {
            generateMemberIntegrationTestsModule(member);
        }

        if (member.config().isHidden()) {
            Utils.skipInstallAndDeploy(pom);
        }
        persistPom(pom);
    }

    private void generateMemberBom(PlatformMemberImpl member) throws MojoExecutionException {
        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(member.baseModel) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        member.baseModel.addModule(moduleName);
        final Path platformBomXml = member.baseModel.getProjectDirectory().toPath().resolve(moduleName).resolve("pom.xml");
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
            final Artifact prevBomCoords = member.previousLastUpdatedBom();
            if (prevBomCoords == null) {
                member.bomChanged = true;
            } else if (!member.config().getRelease().getNext()
                    .equals(member.config().getRelease().getLastDetectedBomUpdate())) {
                final List<org.eclipse.aether.graph.Dependency> prevDeps;
                try {
                    prevDeps = nonWorkspaceResolver.resolveDescriptor(prevBomCoords).getManagedDependencies();
                } catch (BootstrapMavenException e) {
                    throw new MojoExecutionException("Failed to resolve " + prevBomCoords, e);
                }
                if (prevDeps.isEmpty()) {
                    // failed to resolve
                    member.bomChanged = true;
                } else {
                    final Set<ArtifactCoords> prevArtifacts = new HashSet<>(prevDeps.size());
                    for (int i = 0; i < prevDeps.size(); ++i) {
                        final org.eclipse.aether.graph.Dependency d = prevDeps.get(i);
                        final Artifact a = d.getArtifact();
                        if (!isIrrelevantConstraint(a)) {
                            prevArtifacts.add(toCoords(a));
                        }
                    }

                    final Set<ArtifactCoords> currentArtifacts = new HashSet<>(prevArtifacts.size());
                    for (ProjectRelease r : member.generatedBom.releases()) {
                        for (ProjectDependency d : r.dependencies()) {
                            final Artifact a = d.artifact();
                            if (!isIrrelevantConstraint(a)) {
                                currentArtifacts.add(toCoords(a));
                            }
                        }
                    }
                    member.bomChanged = !prevArtifacts.equals(currentArtifacts);
                }
            }
        }
    }

    private Profile getGeneratedBomReleaseProfile() {
        if (generatedBomReleaseProfile == null) {
            Profile parentReleaseProfile = null;
            for (Profile p : project.getModel().getProfiles()) {
                if (p.getId().equals("release")) {
                    parentReleaseProfile = p;
                    break;
                }
            }
            if (parentReleaseProfile == null) {
                getLog().debug("Failed to locate profile with id 'release'");
                return null;
            }
            Plugin gpgPlugin = null;
            for (Plugin plugin : parentReleaseProfile.getBuild().getPlugins()) {
                if (plugin.getArtifactId().equals("maven-gpg-plugin")) {
                    if (plugin.getVersion() == null) {
                        final Plugin managedGpgPlugin = project.getPluginManagement().getPluginsAsMap()
                                .get("org.apache.maven.plugins:maven-gpg-plugin");
                        if (managedGpgPlugin == null) {
                            getLog().warn("Failed to determine the version for org.apache.maven.plugins:maven-gpg-plugin");
                        }
                        plugin = plugin.clone();
                        plugin.setVersion(managedGpgPlugin.getVersion());
                    }
                    gpgPlugin = plugin;
                    break;
                }
            }
            if (gpgPlugin == null) {
                getLog().warn("Failed to locate the maven-gpg-plugin plugin in the " + parentReleaseProfile.getId()
                        + " profile");
                return null;
            }
            final Profile memberReleaseProfile = new Profile();
            memberReleaseProfile.setId(parentReleaseProfile.getId());
            final Build build = new Build();
            memberReleaseProfile.setBuild(build);
            build.addPlugin(gpgPlugin);
            generatedBomReleaseProfile = memberReleaseProfile;
        }
        return generatedBomReleaseProfile;
    }

    private static ArtifactCoords toCoords(final Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private static boolean isIrrelevantConstraint(final Artifact a) {
        return !a.getExtension().equals(ArtifactCoords.TYPE_JAR)
                || PlatformArtifacts.isCatalogArtifactId(a.getArtifactId())
                || a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                || "sources".equals(a.getClassifier())
                || "javadoc".equals(a.getClassifier());
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

    private void generateMemberIntegrationTestsModule(PlatformMemberImpl member)
            throws MojoExecutionException {

        final Model parentPom = member.baseModel;
        final String moduleName = "integration-tests";

        final Model pom = newModel();
        pom.setArtifactId(getArtifactIdBase(parentPom) + "-" + moduleName + "-parent");
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(moduleName) + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(
                pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        final DependencyManagement dm = new DependencyManagement();
        pom.setDependencyManagement(dm);

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
                testCatalogFile = nonWorkspaceResolver().resolve(testCatalogArtifact).getArtifact().getFile();
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to resolve test catalog artifact " + testCatalogArtifact, e);
            }

            final Document testCatalogDoc;
            try (BufferedReader reader = new BufferedReader(new FileReader(testCatalogFile))) {
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
                            testCoords.getGroupId() + ":" + testCoords.getArtifactId() + ":" + testCoords.getVersion());
                    testConfigs.put(testCoords.getKey(), testConfig);
                }
            }
        }

        for (PlatformMemberTestConfig testConfig : testConfigs.values()) {
            if (member.config().getDefaultTestConfig() != null) {
                testConfig.applyDefaults(member.config().getDefaultTestConfig());
            }
            if (!testConfig.isExcluded()) {
                generateIntegrationTestModule(ArtifactCoords.fromString(testConfig.getArtifact()), testConfig, pom);
            }
        }

        Utils.skipInstallAndDeploy(pom);
        persistPom(pom);
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

    private void generateIntegrationTestModule(ArtifactCoords testArtifact,
            PlatformMemberTestConfig testConfig,
            Model parentPom)
            throws MojoExecutionException {

        final String moduleName;
        if (parentPom.getModules().contains(testArtifact.getArtifactId())) {
            String tmp = testArtifact.getArtifactId() + "-" + testArtifact.getVersion();
            if (parentPom.getModules().contains(tmp)) {
                throw new MojoExecutionException("The same test " + testArtifact + " appears to be added twice");
            }
            moduleName = tmp;
            getLog().warn("Using " + moduleName + " as the module name for " + testArtifact + " since "
                    + testArtifact.getArtifactId() + " module name already exists");
        } else {
            moduleName = testArtifact.getArtifactId();
        }
        parentPom.addModule(moduleName);

        final Model pom = newModel();
        pom.setArtifactId(moduleName);
        pom.setName(getNameBase(parentPom) + " " + moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(
                pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

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
        pom.addDependency(appArtifactDep);

        final Dependency testArtifactDep = new Dependency();
        testArtifactDep.setGroupId(testArtifact.getGroupId());
        testArtifactDep.setArtifactId(testArtifact.getArtifactId());
        testArtifactDep.setClassifier("tests");
        testArtifactDep.setType("test-jar");
        testArtifactDep.setVersion(testArtifactVersion);
        testArtifactDep.setScope("test");
        pom.addDependency(testArtifactDep);

        addDependencies(pom, testConfig.getDependencies(), false);
        addDependencies(pom, testConfig.getTestDependencies(), true);

        final Xpp3Dom depsToScan = new Xpp3Dom("dependenciesToScan");
        depsToScan.addChild(textDomElement("dependency", testArtifact.getGroupId() + ":" + testArtifact.getArtifactId()));

        if (!testConfig.isSkipJvm()) {
            final Build build = new Build();
            pom.setBuild(build);

            if (testConfig.isMavenFailsafePlugin()) {
                build.addPlugin(createFailsafeConfig(testConfig, depsToScan, false));
            } else {
                Plugin plugin = new Plugin();
                build.addPlugin(plugin);
                plugin.setGroupId("org.apache.maven.plugins");
                plugin.setArtifactId("maven-surefire-plugin");

                Xpp3Dom config = new Xpp3Dom("configuration");
                plugin.setConfiguration(config);
                config.addChild(depsToScan);

                Xpp3Dom systemProps = null;
                if (!testConfig.getSystemProperties().isEmpty()) {
                    systemProps = new Xpp3Dom("systemPropertyVariables");
                    config.addChild(systemProps);
                    addSystemProperties(systemProps, testConfig.getSystemProperties());
                }
                if (!testConfig.getJvmSystemProperties().isEmpty()) {
                    if (systemProps == null) {
                        systemProps = new Xpp3Dom("systemPropertyVariables");
                        config.addChild(systemProps);
                    }
                    addSystemProperties(systemProps, testConfig.getJvmSystemProperties());
                }

                addGroupsConfig(testConfig, config, false);
                addIncludesExcludesConfig(testConfig, config, false);
            }

            try {
                for (org.eclipse.aether.graph.Dependency d : nonWorkspaceResolver()
                        .resolveDescriptor(toPomArtifact(testArtifact)).getDependencies()) {
                    if (!d.getScope().equals("test")) {
                        continue;
                    }
                    final Artifact a = d.getArtifact();
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
                    if (!universalBomDepKeys.contains(
                            ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension()))) {
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
            final BuildBase buildBase = new BuildBase();
            profile.setBuild(buildBase);

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

            Xpp3Dom config = new Xpp3Dom("configuration");
            exec.setConfiguration(config);
            if (testConfig.isSkip()) {
                config.addChild(textDomElement("skip", "true"));
            }
            config.addChild(textDomElement("appArtifact",
                    testArtifact.getGroupId() + ":" + testArtifact.getArtifactId() + ":" + testArtifactVersion));
        }

        Utils.disablePlugin(pom, "maven-jar-plugin", "default-jar");
        Utils.disablePlugin(pom, "maven-source-plugin", "attach-sources");
        if (testConfig.isPackageApplication()) {
            addQuarkusBuildConfig(pom, appArtifactDep);
        }
        persistPom(pom);

        if (testConfig.getTransformWith() != null) {
            final Path xsl = Paths.get(testConfig.getTransformWith()).toAbsolutePath();
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
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(quarkusCore.originalBomCoords().getGroupId());
        plugin.setArtifactId("quarkus-maven-plugin");
        plugin.setVersion(quarkusCore.getVersionProperty());
        PluginExecution e = new PluginExecution();
        plugin.addExecution(e);
        e.addGoal("build");
        final Xpp3Dom config = new Xpp3Dom("configuration");
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
     * @param testArtifact test artifact coords
     * @return property expression or the actual version
     * @throws MojoExecutionException in case of a failure
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
            versionProp = pomPropsByValues.get(artifactGroupId + ":" + version);
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
                final String groupId = getTestArtifactGroupIdForProperty(name);
                if (groupId == null) {
                    continue;
                }
                if (previous.isEmpty()) {
                    pomPropsByValues.putIfAbsent(groupId + ":" + value, name);
                    continue;
                }
                final String previousGroupId = getTestArtifactGroupIdForProperty(previous);
                if (previousGroupId == null) {
                    pomPropsByValues.putIfAbsent(value, name);
                }

                pomPropsByValues.put(value, "");
                pomPropsByValues.put(previousGroupId + ":" + value, previous);
                pomPropsByValues.putIfAbsent(groupId + ":" + value, name);
            }
        }
    }

    private String getTestArtifactGroupIdForProperty(final String versionProperty) {
        for (String s : pomLines()) {
            int coordsEnd = s.indexOf(versionProperty);
            if (coordsEnd < 0) {
                continue;
            }
            coordsEnd = s.indexOf("</artifact>", coordsEnd);
            if (coordsEnd < 0) {
                continue;
            }
            int coordsStart = s.indexOf("<artifact>");
            if (coordsStart < 0) {
                continue;
            }
            coordsStart += "<artifact>".length();
            return ArtifactCoords.fromString(s.substring(coordsStart, coordsEnd)).getGroupId();
        }
        return null;
    }

    private void addDependencies(final Model pom, List<String> dependencies, boolean test) {
        Dependency dep;
        if (!dependencies.isEmpty()) {
            for (String depStr : dependencies) {
                final ArtifactCoords coords = ArtifactCoords.fromString(depStr);
                dep = new Dependency();
                dep.setGroupId(coords.getGroupId());
                dep.setArtifactId(coords.getArtifactId());
                if (!coords.getClassifier().isEmpty()) {
                    dep.setClassifier(coords.getClassifier());
                }
                if (!coords.getType().equals("jar")) {
                    dep.setType(coords.getType());
                }
                if (!universalBomDepKeys.contains(ArtifactKey.of(coords.getGroupId(), coords.getArtifactId(),
                        coords.getClassifier(), coords.getType()))) {
                    dep.setVersion(coords.getVersion());
                }
                if (test) {
                    dep.setScope("test");
                }
                pom.addDependency(dep);
            }
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
                addElements(config, "includes", "include", testConfig.getNativeIncludes());
            }
            if (!testConfig.getNativeExcludes().isEmpty()) {
                addElements(config, "excludes", "exclude", testConfig.getNativeExcludes());
            }
        } else {
            if (!testConfig.getJvmIncludes().isEmpty()) {
                addElements(config, "includes", "include", testConfig.getJvmIncludes());
            }
            if (!testConfig.getJvmExcludes().isEmpty()) {
                addElements(config, "excludes", "exclude", testConfig.getJvmExcludes());
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

        Xpp3Dom config = new Xpp3Dom("configuration");
        plugin.setConfiguration(config);
        config.addChild(depsToScan);

        plugin.setConfiguration(config);
        PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.addGoal("integration-test");
        exec.addGoal("verify");

        config = new Xpp3Dom("configuration");
        exec.setConfiguration(config);
        if (nativeTest) {
            final Xpp3Dom nativeImagePath = new Xpp3Dom("native.image.path");
            getOrCreateChild(config, "systemProperties").addChild(nativeImagePath);
            nativeImagePath.setValue("${project.build.directory}/${project.build.finalName}-runner");
        }
        if (!testConfig.getSystemProperties().isEmpty()) {
            addSystemProperties(getOrCreateChild(config, "systemProperties"), testConfig.getSystemProperties());
        }

        if (nativeTest) {
            if (!testConfig.getNativeSystemProperties().isEmpty()) {
                addSystemProperties(getOrCreateChild(config, "systemProperties"), testConfig.getNativeSystemProperties());
            }
        } else if (!testConfig.getJvmSystemProperties().isEmpty()) {
            addSystemProperties(getOrCreateChild(config, "systemProperties"), testConfig.getJvmSystemProperties());
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

    private void addSystemProperties(Xpp3Dom sysProps, Map<String, String> props) {
        for (Map.Entry<String, String> entry : props.entrySet()) {
            sysProps.addChild(textDomElement(entry.getKey(), entry.getValue()));
        }
    }

    private void generateUniversalPlatformModule(Model parentPom) throws MojoExecutionException {
        final Artifact bomArtifact = getUniversalBomArtifact();
        final String artifactIdBase = getArtifactIdBase(bomArtifact.getArtifactId());
        final String moduleName = artifactIdBase;

        final Model pom = newModel();
        pom.setArtifactId(artifactIdBase + "-parent");
        pom.setPackaging(ArtifactCoords.TYPE_POM);
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(moduleName) + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = getPomFile(parentPom, moduleName);
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        generatePlatformDescriptorModule(
                ArtifactCoords.of(bomArtifact.getGroupId(),
                        PlatformArtifacts.ensureCatalogArtifactId(bomArtifact.getArtifactId()),
                        bomArtifact.getVersion(), "json", bomArtifact.getVersion()),
                pom, true, true, null, null);

        // to make the descriptor pom resolvable during the platform BOM generation, we need to persist the generated POMs
        persistPom(pom);
        persistPom(parentPom);
        generateUniversalPlatformBomModule(pom);

        if (platformConfig.getUniversal().isGeneratePlatformProperties()) {
            final PlatformMemberConfig tmpConfig = new PlatformMemberConfig();
            tmpConfig.setBom(platformConfig.getUniversal().getBom());
            final PlatformMemberImpl tmp = new PlatformMemberImpl(tmpConfig);
            tmp.baseModel = pom;
            generatePlatformPropertiesModule(tmp, false);
        }

        if (platformConfig.getUniversal().isSkipInstall()) {
            Utils.skipInstallAndDeploy(pom);
        }
        persistPom(pom);
    }

    private void generatePlatformDescriptorModule(ArtifactCoords descriptorCoords, Model parentPom,
            boolean addPlatformReleaseConfig, boolean copyQuarkusCoreMetadata, AttachedMavenPluginConfig attachedPlugin,
            PlatformMember member)
            throws MojoExecutionException {
        final String moduleName = "descriptor";
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

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(moduleDir.relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

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

        final Xpp3Dom config = new Xpp3Dom("configuration");
        final String bomArtifact = PlatformArtifacts.ensureBomArtifactId(descriptorCoords.getArtifactId());
        config.addChild(textDomElement("bomArtifactId", bomArtifact));

        config.addChild(textDomElement("quarkusCoreVersion", quarkusCore.getVersionProperty()));
        if (platformConfig.hasUpstreamQuarkusCoreVersion()) {
            config.addChild(textDomElement("upstreamQuarkusCoreVersion", platformConfig.getUpstreamQuarkusCoreVersion()));
        }

        if (addPlatformReleaseConfig) {
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
                        addMemberDescriptorConfig(pom, membersConfig, m.stackDescriptorCoords());
                    }
                }
            }
        }

        ObjectNode overrides = null;
        if (copyQuarkusCoreMetadata) {
            // copy the quarkus-bom metadata
            overrides = CatalogMapperHelper.mapper().createObjectNode();
            final Artifact bom = quarkusCore.originalBomCoords();
            final Path jsonPath = artifactResolver().resolve(new DefaultArtifact(bom.getGroupId(),
                    bom.getArtifactId() + Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX, bom.getVersion(), "json",
                    bom.getVersion())).getArtifact().getFile().toPath();
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
            pom.getProperties().setProperty(MEMBER_LAST_BOM_UPDATE_PROP, member.lastUpdatedBom().getGroupId() + ":"
                    + member.lastUpdatedBom().getArtifactId() + ":" + member.lastUpdatedBom().getVersion());
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
        if (overrides != null) {
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
                    addMetadataOverrideFile(metadataOverrideFiles, moduleDir, Paths.get(s));
                }
                overrideArtifacts.addAll(m.config().getMetadataOverrideArtifacts());
            }
            addMetadataOverrideArtifacts(config, overrideArtifacts);
        } else {
            for (String s : member.config().getMetadataOverrideFiles()) {
                addMetadataOverrideFile(metadataOverrideFiles, moduleDir, Paths.get(s));
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

        final Path pomXml = moduleDir.resolve("pom.xml");
        pom.setPomFile(pomXml.toFile());
        persistPom(pom);
    }

    private void configureFlattenPluginForMetadataArtifacts(final Model pom) {
        configureFlattenPlugin(pom, true, Map.of(
                "dependencyManagement", "remove",
                "dependencies", "remove",
                "mailingLists", "remove"));
    }

    private void addExtensionDependencyCheck(final RedHatExtensionDependencyCheck depCheckConfig,
            final Xpp3Dom config) {
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

    private void generatePlatformPropertiesModule(PlatformMemberImpl member, boolean addPlatformReleaseConfig)
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

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(moduleDir.relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        // for the bom validation to work
        final DependencyManagement dm = new DependencyManagement();
        pom.setDependencyManagement(dm);
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
                originalDm = nonWorkspaceResolver().resolveDescriptor(srcMember.originalBomCoords()).getManagedDependencies();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to resolve " + member.originalBomCoords(), e);
            }
            final Properties tmp = new Properties();
            for (org.eclipse.aether.graph.Dependency d : originalDm) {
                final Artifact a = d.getArtifact();
                if (a.getExtension().equals("properties")
                        && a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                        && a.getArtifactId().startsWith(srcMember.originalBomCoords().getArtifactId())
                        && a.getGroupId().equals(srcMember.originalBomCoords().getGroupId())
                        && a.getVersion().equals(srcMember.originalBomCoords().getVersion())) {
                    try (BufferedReader reader = Files
                            .newBufferedReader(nonWorkspaceResolver.resolve(a).getArtifact().getFile().toPath())) {
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
            final Xpp3Dom config = new Xpp3Dom("configuration");
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
                membersConfig.addChild(textDomElement("member", m.stackDescriptorCoords().toString()));
                final ArtifactCoords bomCoords = PlatformArtifacts.ensureBomArtifact(m.stackDescriptorCoords());
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

        final Path pomXml = moduleDir.resolve("pom.xml");
        pom.setPomFile(pomXml.toFile());
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
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
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

    private void generateUniversalPlatformBomModule(Model parentPom) throws MojoExecutionException {

        final Artifact bomArtifact = getUniversalBomArtifact();
        final PlatformBomGeneratorConfig bomGen = platformConfig.getBomGenerator();
        final PlatformBomConfig.Builder configBuilder = PlatformBomConfig.builder()
                .artifactResolver(artifactResolver())
                .pomResolver(PomSource.of(bomArtifact))
                .includePlatformProperties(platformConfig.getUniversal().isGeneratePlatformProperties())
                .platformBom(bomArtifact)
                .enableNonMemberQuarkiverseExtensions(bomGen.enableNonMemberQuarkiverseExtensions);

        if (platformConfig.getBomGenerator() != null) {
            configBuilder.disableGroupAlignmentToPreferredVersions(
                    platformConfig.getBomGenerator().disableGroupAlignmentToPreferredVersions);
        }
        for (PlatformMember member : members.values()) {
            configBuilder.addMember(member);
        }

        if (bomGen != null) {
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

        final PlatformBomConfig config = configBuilder.build();

        PlatformBomComposer bomComposer;
        try {
            bomComposer = new PlatformBomComposer(config, new MojoMessageWriter(getLog()));
        } catch (BomDecomposerException e) {
            throw new MojoExecutionException("Failed to generate the platform BOM", e);
        }
        universalGeneratedBom = bomComposer.platformBom();

        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(parentPom) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        parentPom.addModule(moduleName);
        universalPlatformBomXml = parentPom.getProjectDirectory().toPath().resolve(moduleName).resolve("pom.xml");

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
                universalBomDepKeys.add(d.key());
            }
        }
    }

    private Artifact getUniversalBomArtifact() {
        return universalBom == null ? universalBom = toPomArtifact(platformConfig.getUniversal().getBom()) : universalBom;
    }

    private PlatformCatalogResolver catalogResolver() throws MojoExecutionException {
        return catalogs == null ? catalogs = new PlatformCatalogResolver(mavenArtifactResolver()) : catalogs;
    }

    private MavenArtifactResolver nonWorkspaceResolver() throws MojoExecutionException {
        if (nonWorkspaceResolver != null) {
            return nonWorkspaceResolver;
        }
        try {
            return nonWorkspaceResolver = MavenArtifactResolver.builder()
                    .setRepositorySystem(repoSystem)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setWorkspaceDiscovery(false)
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }
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
                int microDot = projectVersion.lastIndexOf('.');
                while (microDot > 0 && !Character.isDigit(projectVersion.charAt(microDot + 1))) {
                    microDot = projectVersion.lastIndexOf('.', microDot - 1);
                }
                tmp.setStream(microDot < 0 ? projectVersion : projectVersion.substring(0, microDot));
            }
            if (tmp.getVersion() == null) {
                tmp.setVersion(project.getVersion());
            }
            platformReleaseConfig = tmp;
        }
        return platformReleaseConfig;
    }

    private void resetResolver() {
        mavenResolver = null;
        artifactResolver = null;
    }

    private ArtifactResolver artifactResolver() throws MojoExecutionException {
        if (mavenResolver == null) {
            artifactResolver = null;
        }
        return artifactResolver == null
                ? artifactResolver = ArtifactResolverProvider.get(mavenArtifactResolver(), null)
                : artifactResolver;
    }

    private MavenArtifactResolver mavenArtifactResolver() throws MojoExecutionException {
        if (mavenResolver != null) {
            return mavenResolver;
        }
        try {
            return mavenResolver = MavenArtifactResolver.builder()
                    .setRepositorySystem(repoSystem)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setCurrentProject(new File(outputDir, "pom.xml").toString())
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }
    }

    private class PlatformMemberImpl implements PlatformMember {

        final PlatformMemberConfig config;
        private final Artifact originalBomCoords;
        private Artifact generatedBomCoords;
        private ArtifactCoords descriptorCoords;
        private ArtifactCoords propertiesCoords;
        private ArtifactCoords stackDescriptorCoords;
        private ArtifactKey key;
        private Model baseModel;
        private DecomposedBom originalBom;
        private DecomposedBom generatedBom;
        private Model generatedBomModel;
        private Path generatedPomFile;
        private String versionProperty;
        private Artifact prevBomRelease;
        private boolean bomChanged;
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
            if (originalBomCoords() != null) {
                return List.of(originalBomCoords().getGroupId());
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
                final String prev = config.getRelease().getLastDetectedBomUpdate();
                if (prev != null) {
                    prevBomRelease = toPomArtifact(prev);
                }
            }
            return prevBomRelease;
        }

        @Override
        public Artifact lastUpdatedBom() {
            return bomChanged || previousLastUpdatedBom() == null ? generatedBomCoords() : previousLastUpdatedBom();
        }

        @Override
        public Artifact originalBomCoords() {
            return originalBomCoords;
        }

        @Override
        public Artifact generatedBomCoords() {
            return generatedBomCoords == null
                    ? generatedBomCoords = toPomArtifact(config.getGeneratedBom(getUniversalBomArtifact().getGroupId()))
                    : generatedBomCoords;
        }

        @Override
        public ArtifactKey key() {
            if (key == null) {
                key = originalBomCoords() == null ? toKey(generatedBomCoords()) : toKey(originalBomCoords());
            }
            return key;
        }

        @Override
        public List<org.eclipse.aether.graph.Dependency> inputConstraints() {
            if (inputConstraints == null) {
                final List<org.eclipse.aether.graph.Dependency> dm = config.getDependencyManagement().toAetherDependencies();
                if (originalBomCoords() == null) {
                    inputConstraints = dm;
                } else if (dm.isEmpty()) {
                    inputConstraints = Collections
                            .singletonList(new org.eclipse.aether.graph.Dependency(originalBomCoords(), "import"));
                } else {
                    if (originalBomCoords() != null) {
                        dm.add(new org.eclipse.aether.graph.Dependency(originalBomCoords(), "import"));
                    }
                    inputConstraints = dm;
                }
            }
            return inputConstraints;
        }

        @Override
        public ArtifactCoords stackDescriptorCoords() {
            if (stackDescriptorCoords != null) {
                return stackDescriptorCoords;
            }
            final String currentCoords = config.getRelease().getNext();
            final String currentVersion = ArtifactCoords.fromString(currentCoords).getVersion();
            return stackDescriptorCoords = ArtifactCoords.of(generatedBomCoords().getGroupId(),
                    generatedBomCoords().getArtifactId() + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                    currentVersion, "json", currentVersion);
        }

        @Override
        public ArtifactCoords descriptorCoords() {
            return descriptorCoords == null
                    ? descriptorCoords = ArtifactCoords.of(generatedBomCoords().getGroupId(),
                            generatedBomCoords().getArtifactId() + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                            generatedBomCoords().getVersion(), "json", generatedBomCoords().getVersion())
                    : descriptorCoords;
        }

        @Override
        public ArtifactCoords propertiesCoords() {
            return propertiesCoords == null
                    ? propertiesCoords = ArtifactCoords.of(generatedBomCoords().getGroupId(),
                            generatedBomCoords().getArtifactId() + BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX,
                            null, "properties", generatedBomCoords().getVersion())
                    : propertiesCoords;
        }

        @Override
        public String getVersionProperty() {
            if (versionProperty == null) {
                final Artifact quarkusBom = quarkusCore.originalBomCoords();
                versionProperty = getTestArtifactVersion(quarkusBom.getGroupId(), quarkusBom.getVersion());
                if (versionProperty.equals(quarkusBom.getVersion())) {
                    final String ga = quarkusBom.getGroupId() + ":" + quarkusBom.getArtifactId() + ":";
                    for (String l : pomLines()) {
                        if (l.startsWith(ga)) {
                            versionProperty = ArtifactCoords.fromString(l).getVersion();
                            break;
                        }
                    }
                }
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

    private static String getDependencyVersion(Model pom, ArtifactCoords coords) {
        return ModelUtils.getVersion(pom).equals(coords.getVersion()) ? "${project.version}" : coords.getVersion();
    }

    private static ArtifactKey toKey(Artifact a) {
        return ArtifactKey.ga(a.getGroupId(), a.getArtifactId());
    }

    private static DefaultArtifact toPomArtifact(String coords) {
        return toPomArtifact(ArtifactCoords.fromString(coords));
    }

    private static DefaultArtifact toPomArtifact(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), ArtifactCoords.DEFAULT_CLASSIFIER,
                ArtifactCoords.TYPE_POM, coords.getVersion());
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
}
