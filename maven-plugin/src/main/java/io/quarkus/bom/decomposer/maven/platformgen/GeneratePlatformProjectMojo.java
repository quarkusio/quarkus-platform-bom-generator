package io.quarkus.bom.decomposer.maven.platformgen;

import static io.quarkus.bom.decomposer.maven.util.Utils.newModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.maven.GeneratePlatformBomMojo;
import io.quarkus.bom.decomposer.maven.MojoMessageWriter;
import io.quarkus.bom.decomposer.maven.util.Utils;
import io.quarkus.bom.platform.PlatformBomComposer;
import io.quarkus.bom.platform.PlatformBomConfig;
import io.quarkus.bom.platform.PlatformBomMemberConfig;
import io.quarkus.bom.platform.PlatformBomUtils;
import io.quarkus.bom.platform.PlatformCatalogResolver;
import io.quarkus.bom.platform.ReportIndexPageGenerator;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import io.quarkus.registry.Constants;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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

    @Parameter(required = true)
    PlatformConfig platformConfig;
    private PlatformReleaseConfig platformReleaseConfig;

    @Parameter(required = true, defaultValue = "${project.build.directory}/updated-pom.xml")
    File updatedPom;

    Artifact universalBom;
    MavenArtifactResolver nonWorkspaceResolver;
    MavenArtifactResolver mavenResolver;
    ArtifactResolver artifactResolver;

    PlatformCatalogResolver catalogs;
    final Map<ArtifactKey, PlatformMember> members = new LinkedHashMap<>();

    private PlatformMember quarkusCore;

    private DecomposedBom universalGeneratedBom;

    private Path universalPlatformBomXml;

    private PluginDescriptor pluginDescr;

    private List<String> pomLines;

    @Parameter(property = "recordUpdatedBoms")
    private boolean recordUpdatedBoms;

    private Set<AppArtifactKey> universalBomDepKeys = new HashSet<>();

    private TransformerFactory transformerFactory;

    // POM property names by values
    private Map<String, String> pomPropsByValues = new HashMap<>();

    private Profile generatedBomReleaseProfile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (session.getRequest().getGoals().contains("clean")) {
            getLog().info("Deleting " + outputDir);
            IoUtils.recursiveDelete(outputDir.toPath());
        }

        quarkusCore = new PlatformMember(platformConfig.getCore());
        members.put(quarkusCore.key(), quarkusCore);
        for (PlatformMemberConfig memberConfig : platformConfig.getMembers()) {
            if (memberConfig.isEnabled()) {
                final PlatformMember member = new PlatformMember(memberConfig);
                members.put(member.key(), member);
            }
        }

        final Model pom = newModel();
        final String rootArtifactIdBase = getArtifactIdBase(project.getModel());
        pom.setArtifactId(rootArtifactIdBase + "-parent");
        pom.setPackaging("pom");
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
        plugin.setVersion(pluginDescriptor().getVersion());
        plugin.setExtensions(true);

        generateUniversalPlatformModule(pom);

        for (PlatformMember member : members.values()) {
            generateMemberModule(member, pom);
        }

        for (PlatformMember member : members.values()) {
            generatePlatformDescriptorModule(member.descriptorCoords(), member.baseModel, true,
                    quarkusCore.originalBomCoords().equals(member.originalBomCoords()),
                    platformConfig.getAttachedMavenPlugin(), member);
            generatePlatformPropertiesModule(member, true);
            persistPom(member.baseModel);
        }

        if (platformConfig.getAttachedMavenPlugin() != null) {
            generateMavenPluginModule(pom);
        }

        addReleaseProfile(pom);
        persistPom(pom);

        recordUpdatedBoms();

        final Path reportsOutputDir = reportsDir.toPath();
        // reset the resolver to pick up all the generated platform modules
        //resetResolver();
        try (ReportIndexPageGenerator index = new ReportIndexPageGenerator(
                reportsOutputDir.resolve("index.html"))) {

            final Path releasesReport = reportsOutputDir.resolve("main").resolve("generated-releases.html");
            GeneratePlatformBomMojo.generateReleasesReport(universalGeneratedBom, releasesReport);
            index.universalBom(universalPlatformBomXml.toUri().toURL(), universalGeneratedBom, releasesReport);

            for (PlatformMember member : members.values()) {
                if (member.originalBomCoords() == null) {
                    continue;
                }
                GeneratePlatformBomMojo.generateBomReports(member.originalBom, member.generatedBom,
                        reportsOutputDir.resolve(member.config.getName().toLowerCase()), index,
                        member.generatedPomFile, artifactResolver());
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate platform member BOM reports", e);
        }
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

        for (PlatformMember member : members.values()) {
            if (!member.config.getName().equals(quarkusCore.config.getName())) {
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

    private void updatePreviousMemberRelease(PlatformMember member, int membersIndex) throws MojoExecutionException {
        if (!member.bomChanged) {
            return;
        }
        final int memberIndex = pomLineContaining("<name>" + member.config.getName() + "</name>", membersIndex);
        if (memberIndex < 0) {
            throw new MojoExecutionException(
                    "Failed to locate member configuration with <name>" + member.config.getName() + "</name>");
        }
        final int releaseIndex = pomLineContaining("<release>", memberIndex);
        if (releaseIndex < 0) {
            throw new MojoExecutionException("Failed to locate <release> configuration for member with <name>"
                    + member.config.getName() + "</name>");
        }
        final int releaseEnd = pomLineContaining("</release>", releaseIndex);
        if (releaseEnd < 0) {
            throw new MojoExecutionException("Failed to locate the closing </release> for member with <name>"
                    + member.config.getName() + "</name>");
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
        final Model pom = newModel();
        pom.setArtifactId(moduleName);
        pom.setPackaging("maven-plugin");
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(targetCoords.getArtifactId()));
        parentPom.addModule(moduleName);

        final File pomXml = new File(new File(parentPom.getProjectDirectory(), moduleName), "pom.xml");
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        final Build build = new Build();
        pom.setBuild(build);
        final Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        final PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("generate-resources");
        exec.addGoal("attach-maven-plugin");

        final Xpp3Dom config = new Xpp3Dom("configuration");
        exec.setConfiguration(config);

        Xpp3Dom e = new Xpp3Dom("originalPluginCoords");
        e.setValue(platformConfig.getAttachedMavenPlugin().getOriginalPluginCoords());
        config.addChild(e);

        e = new Xpp3Dom("targetPluginCoords");
        e.setValue(platformConfig.getAttachedMavenPlugin().getTargetPluginCoords());
        config.addChild(e);

        persistPom(pom);
    }

    private void generateMemberModule(PlatformMember member, Model parentPom) throws MojoExecutionException {

        final String moduleName = getArtifactIdBase(member.generatedBomCoords().getArtifactId());

        final Model pom = newModel();

        if (!member.generatedBomCoords().getGroupId().equals(project.getGroupId())) {
            pom.setGroupId(member.generatedBomCoords().getGroupId());
        }
        pom.setArtifactId(moduleName + "-parent");
        if (!member.generatedBomCoords().getVersion().equals(project.getVersion())) {
            pom.setVersion(member.generatedBomCoords().getVersion());
        }

        pom.setPackaging("pom");
        pom.setName(getNameBase(parentPom) + " " + member.config.getName() + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = new File(new File(parentPom.getProjectDirectory(), moduleName), "pom.xml");
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        member.baseModel = pom;

        generateMemberBom(member);

        if (member.config.hasTests()) {
            generateMemberIntegrationTestsModule(member);
        }

        if (member.config.isHidden()) {
            Utils.skipInstallAndDeploy(pom);
        }
        persistPom(pom);
    }

    private void generateMemberBom(PlatformMember member) throws MojoExecutionException {
        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(member.baseModel) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        member.baseModel.addModule(moduleName);
        final Path platformBomXml = member.baseModel.getProjectDirectory().toPath().resolve(moduleName).resolve("pom.xml");
        member.generatedBomModel = PlatformBomUtils.toPlatformModel(member.generatedBom, baseModel, catalogResolver());
        addReleaseProfile(member.generatedBomModel);

        if (member.config.isHidden()) {
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
            } else if (!member.config.getRelease().getNext().equals(member.config.getRelease().getLastDetectedBomUpdate())) {
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
                getLog().warn("Failed to locate profile with id 'release'");
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
        return new ArtifactCoords(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private static boolean isIrrelevantConstraint(final Artifact a) {
        return !a.getExtension().equals("jar")
                || PlatformArtifacts.isCatalogArtifactId(a.getArtifactId())
                || a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                || "sources".equals(a.getClassifier())
                || "javadoc".equals(a.getClassifier());
    }

    private List<String> pomLines() throws MojoExecutionException {
        if (pomLines != null) {
            return pomLines;
        }
        try {
            return pomLines = Files.readAllLines(project.getFile().toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + project.getFile(), e);
        }
    }

    private int pomLineContaining(String text, int fromLine)
            throws MojoExecutionException {
        return pomLineContaining(text, fromLine, Integer.MAX_VALUE);
    }

    private int pomLineContaining(String text, int fromLine, int toLine)
            throws MojoExecutionException {
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

    private void generateMemberIntegrationTestsModule(PlatformMember member)
            throws MojoExecutionException {

        final Model parentPom = member.baseModel;
        final String moduleName = "integration-tests";

        final Model pom = newModel();
        pom.setArtifactId(getArtifactIdBase(parentPom) + "-" + moduleName + "-parent");
        pom.setPackaging("pom");
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(moduleName) + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = new File(new File(parentPom.getProjectDirectory(), moduleName), "pom.xml");
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

        final Map<ArtifactCoords, PlatformMemberTestConfig> testConfigs = new LinkedHashMap<>();
        for (PlatformMemberTestConfig test : member.config.getTests()) {
            testConfigs.put(ArtifactCoords.fromString(test.getArtifact()), test);
        }

        if (member.config.getTestCatalogArtifact() != null) {
            final Artifact testCatalogArtifact = toAetherArtifact(member.config.getTestCatalogArtifact());
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
                final ArtifactCoords testCoords = new ArtifactCoords(testGroupId, testArtifactId, null, "jar",
                        member.originalBomCoords.getVersion());
                // add it unless it's overriden in the config
                PlatformMemberTestConfig testConfig = testConfigs.get(testCoords);
                if (testConfig == null) {
                    testConfig = new PlatformMemberTestConfig();
                    testConfig.setArtifact(
                            testCoords.getGroupId() + ":" + testCoords.getArtifactId() + ":" + testCoords.getVersion());
                    testConfigs.put(testCoords, testConfig);
                }
            }
        }

        for (Map.Entry<ArtifactCoords, PlatformMemberTestConfig> test : testConfigs.entrySet()) {
            final PlatformMemberTestConfig testConfig = test.getValue();
            if (member.config.getDefaultTestConfig() != null) {
                testConfig.applyDefaults(member.config.getDefaultTestConfig());
            }
            if (!testConfig.isExcluded()) {
                generateIntegrationTestModule(test.getKey(), testConfig, pom);
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
        bomDep.setType("pom");
        bomDep.setScope("import");
        return bomDep;
    }

    private void generateIntegrationTestModule(ArtifactCoords testArtifact,
            PlatformMemberTestConfig testConfig,
            Model parentPom)
            throws MojoExecutionException {
        final String moduleName = testArtifact.getArtifactId();

        final Model pom = newModel();
        pom.setArtifactId(moduleName);
        pom.setName(getNameBase(parentPom) + " " + moduleName);
        parentPom.addModule(moduleName);

        final File pomXml = new File(new File(parentPom.getProjectDirectory(), moduleName), "pom.xml");
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

        Dependency dep = new Dependency();
        dep.setGroupId(testArtifact.getGroupId());
        dep.setArtifactId(testArtifact.getArtifactId());
        if (!testArtifact.getClassifier().isEmpty()) {
            dep.setClassifier(testArtifact.getClassifier());
        }
        dep.setType(testArtifact.getType());
        dep.setVersion(testArtifactVersion);
        pom.addDependency(dep);

        dep = new Dependency();
        dep.setGroupId(testArtifact.getGroupId());
        dep.setArtifactId(testArtifact.getArtifactId());
        dep.setClassifier("tests");
        dep.setType("test-jar");
        dep.setVersion(testArtifactVersion);
        dep.setScope("test");
        pom.addDependency(dep);

        addDependencies(pom, testConfig.getDependencies(), false);
        addDependencies(pom, testConfig.getTestDependencies(), true);

        final Xpp3Dom depsToScan = new Xpp3Dom("dependenciesToScan");
        final Xpp3Dom testDep = new Xpp3Dom("dependency");
        depsToScan.addChild(testDep);
        testDep.setValue(testArtifact.getGroupId() + ":" + testArtifact.getArtifactId());

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
                    if ("pom".equals(a.getExtension()) && !d.getExclusions().isEmpty()) {
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
                            new AppArtifactKey(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension()))) {
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
                Xpp3Dom skip = new Xpp3Dom("skip");
                config.addChild(skip);
                skip.setValue("true");
            }
            final Xpp3Dom appArtifact = new Xpp3Dom("appArtifact");
            config.addChild(appArtifact);
            appArtifact.setValue(testArtifact.getGroupId() + ":" + testArtifact.getArtifactId() + ":" + testArtifactVersion);
        }

        Utils.disablePlugin(pom, "maven-jar-plugin", "default-jar");
        Utils.disablePlugin(pom, "maven-source-plugin", "attach-sources");
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
    }

    /**
     * Returns either a property expression that should be used in place of the actual artifact version
     * or the actual artifact version, in case no property was found that could represent the version
     * 
     * @param testArtifact test artifact coords
     * @return property expression or the actual version
     * @throws MojoExecutionException in case of a failure
     */
    private String getTestArtifactVersion(String artifactGroupId, String version) throws MojoExecutionException {
        if (pomPropsByValues.isEmpty()) {
            for (Map.Entry<?, ?> prop : project.getOriginalModel().getProperties().entrySet()) {
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
        String versionProp = pomPropsByValues.get(version);
        if (versionProp == null) {
            return version;
        }
        if (versionProp.isEmpty()) {
            versionProp = pomPropsByValues.get(artifactGroupId + ":" + version);
        }
        return "${" + versionProp + "}";
    }

    private String getTestArtifactGroupIdForProperty(final String versionProperty)
            throws MojoExecutionException {
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
                if (!universalBomDepKeys.contains(new AppArtifactKey(coords.getGroupId(), coords.getArtifactId(),
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
        final Xpp3Dom groups = new Xpp3Dom("groups");
        config.addChild(groups);
        groups.setValue(groupsStr);
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
            final Xpp3Dom e = new Xpp3Dom(elementName);
            e.setValue(s);
            includes.addChild(e);
        }
    }

    private TransformerFactory getTransformerFactory() throws TransformerFactoryConfigurationError {
        if (transformerFactory == null) {
            final TransformerFactory factory = TransformerFactory.newInstance();
            //factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            //factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            transformerFactory = factory;
        }
        return transformerFactory;
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
            final Xpp3Dom e = new Xpp3Dom(entry.getKey());
            e.setValue(entry.getValue());
            sysProps.addChild(e);
        }
    }

    private void generateUniversalPlatformModule(Model parentPom) throws MojoExecutionException {
        final Artifact bomArtifact = getUniversalBomArtifact();
        final String artifactIdBase = getArtifactIdBase(bomArtifact.getArtifactId());
        final String moduleName = artifactIdBase;

        final Model pom = newModel();
        pom.setArtifactId(artifactIdBase + "-parent");
        pom.setPackaging("pom");
        pom.setName(getNameBase(parentPom) + " " + artifactIdToName(moduleName) + " - Parent");
        parentPom.addModule(moduleName);

        final File pomXml = new File(new File(parentPom.getProjectDirectory(), moduleName), "pom.xml");
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(parentPom));
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(parentPom));
        parent.setRelativePath(pomXml.toPath().getParent().relativize(parentPom.getProjectDirectory().toPath()).toString());
        pom.setParent(parent);

        generatePlatformDescriptorModule(
                new ArtifactCoords(bomArtifact.getGroupId(),
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
            final PlatformMember tmp = new PlatformMember(tmpConfig);
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
        pom.setPackaging("pom");
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
        final Xpp3Dom bomArtifactId = new Xpp3Dom("bomArtifactId");
        final String bomArtifact = PlatformArtifacts.ensureBomArtifactId(descriptorCoords.getArtifactId());
        bomArtifactId.setValue(bomArtifact);
        config.addChild(bomArtifactId);

        Xpp3Dom e = new Xpp3Dom("quarkusCoreVersion");
        e.setValue(quarkusCore.getVersionProperty());
        config.addChild(e);
        if (platformConfig.hasUpstreamQuarkusCoreVersion()) {
            e = new Xpp3Dom("upstreamQuarkusCoreVersion");
            e.setValue(platformConfig.getUpstreamQuarkusCoreVersion());
            config.addChild(e);
        }

        if (addPlatformReleaseConfig) {
            final Xpp3Dom stackConfig = new Xpp3Dom("platformRelease");
            config.addChild(stackConfig);
            final Xpp3Dom platformKey = new Xpp3Dom("platformKey");
            stackConfig.addChild(platformKey);
            e = new Xpp3Dom("stream");
            e.setValue("${" + PLATFORM_STREAM_PROP + "}");
            stackConfig.addChild(e);
            e = new Xpp3Dom("version");
            e.setValue("${" + PLATFORM_RELEASE_PROP + "}");
            stackConfig.addChild(e);
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
                    if (!m.config.isHidden()) {
                        addMemberDescriptorConfig(pom, membersConfig, m.stackDescriptorCoords());
                    }
                }
            }
        }

        ObjectNode overrides = null;
        if (copyQuarkusCoreMetadata) {
            // copy the quarkus-bom metadata
            overrides = JsonCatalogMapperHelper.mapper().createObjectNode();
            final Artifact bom = quarkusCore.originalBomCoords();
            final Path jsonPath = artifactResolver().resolve(new DefaultArtifact(bom.getGroupId(),
                    bom.getArtifactId() + Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX, bom.getVersion(), "json",
                    bom.getVersion())).getArtifact().getFile().toPath();
            final JsonNode descriptorNode;
            try (BufferedReader reader = Files.newBufferedReader(jsonPath)) {
                descriptorNode = JsonCatalogMapperHelper.mapper().readTree(reader);
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

        // Update last-bom-update
        if (member != null) {
            pom.getProperties().setProperty(MEMBER_LAST_BOM_UPDATE_PROP, member.lastUpdatedBom().getGroupId() + ":"
                    + member.lastUpdatedBom().getArtifactId() + ":" + member.lastUpdatedBom().getVersion());
            if (overrides == null) {
                overrides = JsonCatalogMapperHelper.mapper().createObjectNode();
            }
            JsonNode metadata = overrides.get("metadata");
            if (metadata == null) {
                metadata = overrides.putObject("metadata");
            }
            final ObjectNode on = (ObjectNode) metadata;
            on.set(LAST_BOM_UPDATE, on.textNode("${" + MEMBER_LAST_BOM_UPDATE_PROP + "}"));
        }

        // METADATA OVERRIDES
        final StringJoiner metadataOverrideFiles = new StringJoiner(",");
        if (overrides != null) {
            Path overridesFile = moduleDir.resolve("src").resolve("main").resolve("resources").resolve("overrides.json");
            try {
                JsonCatalogMapperHelper.serialize(overrides, overridesFile);
            } catch (Exception ex) {
                throw new MojoExecutionException("Failed to serialize metadata to " + overridesFile, ex);
            }
            overridesFile = moduleDir.resolve("target").resolve("classes").resolve(overridesFile.getFileName());
            metadataOverrideFiles.add("${project.basedir}/" + moduleDir.relativize(overridesFile));
        }

        final PlatformDescriptorGeneratorConfig descrGen = platformConfig.getDescriptorGenerator();
        if (descrGen != null && descrGen.overridesFile != null) {
            for (String path : descrGen.overridesFile.split(",")) {
                metadataOverrideFiles.add("${project.basedir}/" + moduleDir.relativize(Paths.get(path.trim())));
            }
        }

        if (member == null) {
            final List<String> overrideArtifacts = new ArrayList<>(0);
            for (PlatformMember m : members.values()) {
                for (String s : m.config.getMetadataOverrideFiles()) {
                    addMetadataOverrideFile(metadataOverrideFiles, moduleDir, Paths.get(s));
                }
                overrideArtifacts.addAll(m.config.getMetadataOverrideArtifacts());
            }
            addMetadataOverrideArtifacts(config, overrideArtifacts);
        } else {
            for (String s : member.config.getMetadataOverrideFiles()) {
                addMetadataOverrideFile(metadataOverrideFiles, moduleDir, Paths.get(s));
            }
            addMetadataOverrideArtifacts(config, member.config.getMetadataOverrideArtifacts());
        }

        if (metadataOverrideFiles.length() > 0) {
            e = new Xpp3Dom("overridesFile");
            e.setValue(metadataOverrideFiles.toString());
            config.addChild(e);
        }

        if (descrGen != null && descrGen.skipCategoryCheck) {
            e = new Xpp3Dom("skipCategoryCheck");
            e.setValue("true");
            config.addChild(e);
            plugin.setConfiguration(config);
        }
        if (descrGen != null && descrGen.resolveDependencyManagement) {
            e = new Xpp3Dom("resolveDependencyManagement");
            e.setValue("true");
            config.addChild(e);
            plugin.setConfiguration(config);
        }

        final Dependency dep = new Dependency();
        dep.setGroupId(descriptorCoords.getGroupId());
        dep.setArtifactId(bomArtifact);
        dep.setType("pom");
        dep.setVersion(getDependencyVersion(pom, descriptorCoords));
        pom.addDependency(dep);

        if (member != null && member.config.isHidden()) {
            Utils.skipInstallAndDeploy(pom);
        }

        final Path pomXml = moduleDir.resolve("pom.xml");
        pom.setPomFile(pomXml.toFile());
        persistPom(pom);
    }

    private void addMetadataOverrideArtifacts(final Xpp3Dom config, final List<String> overrideArtifacts) {
        if (overrideArtifacts.isEmpty()) {
            return;
        }
        final Xpp3Dom artifacts = new Xpp3Dom("metadataOverrideArtifacts");
        config.addChild(artifacts);
        for (String s : overrideArtifacts) {
            final Xpp3Dom e = new Xpp3Dom("artifact");
            e.setValue(s);
            artifacts.addChild(e);
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
        Xpp3Dom e;
        e = new Xpp3Dom("member");
        final String value;
        if (memberCoords.getGroupId().equals(ModelUtils.getGroupId(pom))
                && memberCoords.getVersion().equals(ModelUtils.getVersion(pom))) {
            value = "${project.groupId}:" + memberCoords.getArtifactId() + ":${project.version}:json:${project.version}";
        } else {
            value = memberCoords.toString();
        }
        e.setValue(value);
        membersConfig.addChild(e);
    }

    private void generatePlatformPropertiesModule(PlatformMember member, boolean addPlatformReleaseConfig)
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
        pom.setPackaging("pom");
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
        bom.setType("pom");
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
        if (member.config.getBom() != null) {
            // this is just to copy the core properties to the universal platform
            final PlatformMember srcMember = platformConfig.getUniversal().getBom().equals(member.config.getBom()) ? quarkusCore
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
            Xpp3Dom e = new Xpp3Dom("platformKey");
            e.setValue("${" + PLATFORM_KEY_PROP + "}");
            stackConfig.addChild(e);
            e = new Xpp3Dom("stream");
            e.setValue("${" + PLATFORM_STREAM_PROP + "}");
            stackConfig.addChild(e);
            e = new Xpp3Dom("version");
            e.setValue("${" + PLATFORM_RELEASE_PROP + "}");
            stackConfig.addChild(e);
            final Xpp3Dom membersConfig = new Xpp3Dom("members");
            stackConfig.addChild(membersConfig);
            final Iterator<PlatformMember> i = members.values().iterator();
            final StringBuilder buf = new StringBuilder();
            while (i.hasNext()) {
                final PlatformMember m = i.next();
                if (m.config.isHidden()) {
                    continue;
                }
                if (buf.length() > 0) {
                    buf.append(",");
                }
                e = new Xpp3Dom("member");
                membersConfig.addChild(e);
                e.setValue(m.stackDescriptorCoords().toString());
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

        if (member.config.isHidden()) {
            Utils.skipInstallAndDeploy(pom);
        }

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
                .pomResolver(PomSource.of(bomArtifact))
                .includePlatformProperties(platformConfig.getUniversal().isGeneratePlatformProperties())
                .platformBom(bomArtifact)
                .enableNonMemberQuarkiverseExtensions(bomGen.enableNonMemberQuarkiverseExtensions);

        for (PlatformMember member : members.values()) {
            configBuilder.importBom(member.bomGeneratorMemberConfig());
        }

        if (bomGen != null && bomGen.enforcedDependencies != null) {
            for (String enforced : bomGen.enforcedDependencies) {
                final AppArtifactCoords coords = AppArtifact.fromString(enforced);
                configBuilder.enforce(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                        coords.getType(), coords.getVersion()));
            }
        }
        if (bomGen != null && bomGen.excludedDependencies != null) {
            for (String excluded : bomGen.excludedDependencies) {
                configBuilder.exclude(AppArtifactKey.fromString(excluded));
            }
        }
        if (bomGen != null && bomGen.excludedGroups != null) {
            for (String excluded : bomGen.excludedGroups) {
                configBuilder.excludeGroupId(excluded);
            }
        }

        final PlatformBomConfig config = configBuilder
                .artifactResolver(artifactResolver())
                .build();

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

        quarkusCore.originalBom = bomComposer.originalQuarkusCoreBom();
        quarkusCore.generatedBom = bomComposer.generatedQuarkusCoreBom();

        for (DecomposedBom importedBom : bomComposer.alignedMemberBoms()) {
            final PlatformMember member = members.get(toKey(importedBom.bomArtifact()));
            member.originalBom = bomComposer.originalMemberBom(
                    member.originalBomCoords == null ? member.generatedBomCoords() : member.originalBomCoords);
            member.generatedBom = importedBom;
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

    private class PlatformMember {

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
        public boolean bomChanged;

        PlatformMember(PlatformMemberConfig config) {
            this.config = config;
            if (config.getBom() == null) {
                originalBomCoords = null;
                if (config.getDependencyManagement().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Neither BOM coordinates nor dependencyManagement have been configured for member "
                                    + config.getName());
                }
            } else {
                if (!config.getDependencyManagement().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Either BOM or dependencyManagement are allowed for a platform member: " + config.getName());
                }
                originalBomCoords = toPomArtifact(config.getBom());
            }
        }

        Artifact previousLastUpdatedBom() {
            if (prevBomRelease == null) {
                final String prev = config.getRelease().getLastDetectedBomUpdate();
                if (prev != null) {
                    prevBomRelease = toPomArtifact(prev);
                }
            }
            return prevBomRelease;
        }

        Artifact lastUpdatedBom() {
            return bomChanged || previousLastUpdatedBom() == null ? generatedBomCoords() : previousLastUpdatedBom();
        }

        Artifact originalBomCoords() {
            return originalBomCoords;
        }

        Artifact generatedBomCoords() {
            if (generatedBomCoords == null) {
                if (config.getRelease() == null || config.getRelease().getNext() == null) {
                    generatedBomCoords = new DefaultArtifact(getUniversalBomArtifact().getGroupId(),
                            originalBomCoords().getArtifactId(), null,
                            "pom", originalBomCoords().getVersion());
                } else {
                    generatedBomCoords = toPomArtifact(config.getRelease().getNext());
                }
            }
            return generatedBomCoords;
        }

        ArtifactKey key() {
            return key == null ? key = toKey(generatedBomCoords()) : key;
        }

        PlatformBomMemberConfig bomGeneratorMemberConfig() {
            final PlatformBomMemberConfig bomMember;
            if (originalBomCoords == null) {
                final List<org.eclipse.aether.graph.Dependency> dm = new ArrayList<>(config.getDependencyManagement().size());
                for (String coordsStr : config.getDependencyManagement()) {
                    final ArtifactCoords coords = ArtifactCoords.fromString(coordsStr);
                    dm.add(new org.eclipse.aether.graph.Dependency(new DefaultArtifact(coords.getGroupId(),
                            coords.getArtifactId(), coords.getClassifier(), coords.getType(), coords.getVersion()), "compile"));
                }
                bomMember = new PlatformBomMemberConfig(dm);
            } else {
                bomMember = new PlatformBomMemberConfig(
                        new org.eclipse.aether.graph.Dependency(originalBomCoords(), "import"));
            }
            bomMember.setGeneratedBomArtifact(generatedBomCoords());
            return bomMember;
        }

        ArtifactCoords stackDescriptorCoords() {
            if (stackDescriptorCoords != null) {
                return stackDescriptorCoords;
            }
            final String currentCoords = config.getRelease().getNext();
            final String currentVersion = ArtifactCoords.fromString(currentCoords).getVersion();
            return stackDescriptorCoords = new ArtifactCoords(generatedBomCoords().getGroupId(),
                    generatedBomCoords().getArtifactId() + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                    currentVersion, "json", currentVersion);
        }

        ArtifactCoords descriptorCoords() {
            return descriptorCoords == null
                    ? descriptorCoords = new ArtifactCoords(generatedBomCoords().getGroupId(),
                            generatedBomCoords().getArtifactId() + BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX,
                            generatedBomCoords().getVersion(), "json", generatedBomCoords().getVersion())
                    : descriptorCoords;
        }

        ArtifactCoords propertiesCoords() {
            return propertiesCoords == null
                    ? propertiesCoords = new ArtifactCoords(generatedBomCoords().getGroupId(),
                            generatedBomCoords().getArtifactId() + BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX,
                            null, "properties", generatedBomCoords().getVersion())
                    : propertiesCoords;
        }

        String getVersionProperty() throws MojoExecutionException {
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
    }

    private static String getDependencyVersion(Model pom, ArtifactCoords coords) {
        return ModelUtils.getVersion(pom).equals(coords.getVersion()) ? "${project.version}" : coords.getVersion();
    }

    private static ArtifactKey toKey(Artifact a) {
        return new ArtifactKey(a.getGroupId(), a.getArtifactId());
    }

    private static DefaultArtifact toPomArtifact(String coords) {
        return toPomArtifact(ArtifactCoords.fromString(coords));
    }

    private static DefaultArtifact toPomArtifact(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), null, "pom", coords.getVersion());
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
