package io.quarkus.bom.decomposer.maven.platformgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.maven.GeneratePlatformBomMojo;
import io.quarkus.bom.decomposer.maven.MojoMessageWriter;
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
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import io.quarkus.registry.Constants;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Parameter(required = true, defaultValue = "${project.build.directory}/updated-pom.xml")
    File updatedPom;

    Artifact mainBom;
    MavenArtifactResolver nonWorkspaceResolver;
    MavenArtifactResolver mavenResolver;
    ArtifactResolver artifactResolver;

    PlatformCatalogResolver catalogs;
    Map<ArtifactKey, PlatformMember> members = new HashMap<>();

    private PlatformMember quarkusCore;

    private DecomposedBom mainGeneratedBom;

    private Path mainPlatformBomXml;

    private PluginDescriptor pluginDescr;

    private List<String> pomLines;

    private Boolean bumpVersions;

    private boolean isBumpVersions() {
        // TODO should be checking deploy instead
        return bumpVersions == null ? bumpVersions = session.getRequest().getGoals().contains("install") : bumpVersions;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        quarkusCore = new PlatformMember(platformConfig.core);
        members.put(quarkusCore.key(), quarkusCore);
        for (PlatformMemberConfig memberConfig : platformConfig.members) {
            if (!memberConfig.disabled) {
                final PlatformMember member = new PlatformMember(memberConfig);
                members.put(member.key(), member);
            }
        }

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");
        if (!getMainBomArtifact().getGroupId().equals(project.getGroupId())) {
            pom.setGroupId(getMainBomArtifact().getGroupId());
        }
        String rootArtifactId = getMainBomArtifact().getArtifactId();
        if (rootArtifactId.endsWith("-bom")) {
            rootArtifactId = rootArtifactId.substring(0, rootArtifactId.length() - "-bom".length());
        }
        pom.setArtifactId(rootArtifactId + "-parent");
        if (!getMainBomArtifact().getVersion().equals(project.getVersion())) {
            pom.setVersion(getMainBomArtifact().getVersion());
        }
        pom.setPackaging("pom");
        pom.setName(artifactIdToName(rootArtifactId) + " - Parent");

        final File pomXml = new File(outputDir, "pom.xml");
        pom.setPomFile(pomXml);

        final Parent parent = new Parent();
        parent.setGroupId(project.getGroupId());
        parent.setArtifactId(project.getArtifactId());
        parent.setVersion(project.getVersion());
        parent.setRelativePath(pomXml.toPath().getParent().relativize(project.getFile().getParentFile().toPath()).toString());
        pom.setParent(parent);

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

        generateMainPlatformModule(pom);

        for (PlatformMember member : members.values()) {
            generateMemberModule(member, pom);
        }

        for (PlatformMember member : members.values()) {
            generatePlatformDescriptorModule(member.descriptorCoords(), member.baseModel, true);
            generatePlatformPropertiesModule(member, true);
            persistPom(member.baseModel);
        }

        persistPom(pom);

        if (isBumpVersions()) {
            for (PlatformMember member : members.values()) {
                if (!member.skipDeploy) {
                    int lineIndex = pomLineContaining("<platformRelease>", 0);
                    lineIndex = pomLineContaining("<version>", lineIndex + 1);
                    String versionLine = pomLines().get(lineIndex);
                    final String versionStr = versionLine.substring(versionLine.indexOf("<version>") + "<version>".length(),
                            versionLine.lastIndexOf("</version>"));
                    final int version = Integer.parseInt(versionStr);
                    final StringBuilder buf = new StringBuilder();
                    buf.append(versionLine.substring(0, versionLine.indexOf("<version>") + "<version>".length()))
                            .append(version + 1).append("</version>");
                    pomLines.set(lineIndex, buf.toString());
                    break;
                }
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
            project.setPomFile(updatedPom);
        }

        final Path reportsOutputDir = reportsDir.toPath();
        // reset the resolver to pick up all the generated platform modules
        //resetResolver();
        try (ReportIndexPageGenerator index = new ReportIndexPageGenerator(
                reportsOutputDir.resolve("index.html"))) {

            final Path releasesReport = reportsOutputDir.resolve("main").resolve("generated-releases.html");
            GeneratePlatformBomMojo.generateReleasesReport(mainGeneratedBom, releasesReport);
            index.mainBom(mainPlatformBomXml.toUri().toURL(), mainGeneratedBom, releasesReport);

            for (PlatformMember member : members.values()) {
                if (member.originalBomCoords() == null) {
                    continue;
                }
                GeneratePlatformBomMojo.generateBomReports(member.originalBom, member.generatedBom,
                        reportsOutputDir.resolve(member.config.name.toLowerCase()), index,
                        member.generatedPomFile, artifactResolver());
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate platform member BOM reports", e);
        }
    }

    private void generateMemberModule(PlatformMember member, Model parentPom) throws MojoExecutionException {
        final String moduleName = member.config.name.toLowerCase();

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");
        pom.setArtifactId(getArtifactIdBase(parentPom) + moduleName + "-parent");
        pom.setPackaging("pom");
        pom.setName(getNameBase(parentPom) + " " + member.config.name + " - Parent");
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

        if (member.skipDeploy) {
            skipInstall(pom);
        }

        if (!member.config.tests.isEmpty()) {
            generateMemberIntegrationTestsModule(member);
        }

        persistPom(pom);
    }

    private static void skipInstall(final Model pom) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        PluginManagement pm = build.getPluginManagement();
        if (pm == null) {
            pm = new PluginManagement();
            build.setPluginManagement(pm);
        }
        final Plugin plugin = new Plugin();
        pm.addPlugin(plugin);
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-install-plugin");
        final PluginExecution e = new PluginExecution();
        plugin.addExecution(e);
        e.setId("default-install");
        e.setPhase("none");
    }

    private void generateMemberBom(PlatformMember member) throws MojoExecutionException {
        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(member.baseModel) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        member.baseModel.addModule(moduleName);
        final Path platformBomXml = member.baseModel.getProjectDirectory().toPath().resolve(moduleName).resolve("pom.xml");
        member.generatedBomModel = PlatformBomUtils.toPlatformModel(member.generatedBom, baseModel, catalogResolver());

        try {
            Files.createDirectories(platformBomXml.getParent());
            ModelUtils.persistModel(platformBomXml, member.generatedBomModel);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist generated BOM to " + platformBomXml, e);
        }

        member.generatedPomFile = platformBomXml;

        if (isBumpVersions()) {
            if (member.config.release.previous != null) {
                final Path previousPom;
                try {
                    previousPom = mavenArtifactResolver().resolve(toPomArtifact(member.config.release.previous)).getArtifact()
                            .getFile()
                            .toPath();
                } catch (BootstrapMavenException e) {
                    throw new MojoExecutionException("Failed to resolve " + member.config.release.previous, e);
                } catch (MojoExecutionException e) {
                    throw e;
                }
                final Model previousModel;
                try {
                    previousModel = ModelUtils.readModel(previousPom);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to read " + previousPom, e);
                }

                if (match(member.generatedBomModel.getDependencyManagement().getDependencies(),
                        previousModel.getDependencyManagement().getDependencies())) {
                    // TODO SKIP DEPLOY
                    member.skipDeploy = true;
                } else {
                    int i = pomLineContaining("<name>" + member.config.name + "</name>", 0);
                    final int releasesI = pomLineContaining("<release>", i + 1);
                    final int previousLineIndex = pomLineContaining("<previous>", releasesI + 1);

                    final int offset = pomLines().get(previousLineIndex).indexOf("<previous>");
                    final StringBuilder buf = new StringBuilder();
                    for (int j = 0; j < offset; ++j) {
                        buf.append(' ');
                    }
                    buf.append("<previous>").append(member.config.release.upcoming).append("</previous>");
                    pomLines().set(previousLineIndex, buf.toString());

                    increaseUpcomingVersion(pomLineContaining("<upcoming>", releasesI + 1));
                }
            } else {
                int i = pomLineContaining("<name>" + member.config.name + "</name>", 0);
                i = pomLineContaining("<release>", i + 1);
                i = pomLineContaining("<upcoming>", i + 1);

                final int offset = pomLines().get(i).indexOf("<upcoming>");
                final StringBuilder buf = new StringBuilder();
                for (int j = 0; j < offset; ++j) {
                    buf.append(' ');
                }
                buf.append("<previous>").append(member.config.release.upcoming).append("</previous>");
                pomLines().add(i++, buf.toString());

                increaseUpcomingVersion(i);
            }
        }
    }

    private void increaseUpcomingVersion(int upcomingLineIndex)
            throws MojoExecutionException {
        final String upcomingLine = pomLines().get(upcomingLineIndex);
        int counterStart = upcomingLine.lastIndexOf("SP");
        final int counterEnd = upcomingLine.lastIndexOf("</upcoming>");
        final String increasedCounterStr;
        if (counterStart > 0) {
            final String currentCounterStr = upcomingLine.substring(counterStart + 2, counterEnd);
            final int currentCounter = Integer.parseInt(currentCounterStr);
            increasedCounterStr = String.valueOf(currentCounter + 1);
        } else {
            counterStart = counterEnd - 2;
            increasedCounterStr = ".SP1";
        }
        final StringBuilder buf = new StringBuilder();
        buf.append(upcomingLine.substring(0, counterStart + 2));
        buf.append(increasedCounterStr);
        buf.append("</upcoming>");
        pomLines().set(upcomingLineIndex, buf.toString());
    }

    private static boolean match(List<Dependency> left, List<Dependency> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); ++i) {
            final Dependency l = left.get(i);
            final Dependency r = right.get(i);
            if (!l.getManagementKey().equals(r.getManagementKey())
                    || !l.getVersion().equals(r.getVersion())
                    || l.isOptional() != r.isOptional()) {
                if (PlatformArtifacts.isCatalogArtifactId(l.getArtifactId())
                        && PlatformArtifacts.isCatalogArtifactId(r.getArtifactId())
                        || l.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                                && r.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)) {
                    continue;
                }
                return false;
            }
            if (l.getScope() == null) {
                if (r.getScope() != null) {
                    return false;
                }
            } else if (!l.getScope().equals(r.getScope())) {
                return false;
            }
            final List<Exclusion> le = l.getExclusions();
            final List<Exclusion> re = r.getExclusions();
            if (le.size() != re.size()) {
                return false;
            }
            final Set<String> ls = le.stream().map(e -> e.getGroupId() + ":" + e.getArtifactId()).collect(Collectors.toSet());
            for (Exclusion e : re) {
                if (!ls.contains(e.getGroupId() + ":" + e.getArtifactId())) {
                    return false;
                }
            }
        }
        return true;
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
        final List<String> lines = pomLines();
        while (fromLine < lines.size()) {
            if (lines.get(fromLine).contains(text)) {
                break;
            }
            ++fromLine;
        }
        if (fromLine == lines.size()) {
            throw new MojoExecutionException("Failed to locate " + text + " in " + project.getFile());
        }
        return fromLine;
    }

    private void generateMemberIntegrationTestsModule(PlatformMember member)
            throws MojoExecutionException {

        final Model parentPom = member.baseModel;
        final String moduleName = "integration-tests";

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");
        pom.setArtifactId(getArtifactIdBase(parentPom) + moduleName);
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

        Dependency bomDep = new Dependency();
        final Artifact bom = getMainBomArtifact();
        bomDep.setGroupId(bom.getGroupId());
        bomDep.setArtifactId(bom.getArtifactId());
        bomDep.setVersion(bom.getVersion());
        bomDep.setType("pom");
        bomDep.setScope("import");
        dm.addDependency(bomDep);

        bomDep = new Dependency();
        bomDep.setGroupId("io.quarkus");
        bomDep.setArtifactId("quarkus-integration-test-class-transformer");
        bomDep.setVersion(quarkusCoreVersion());
        dm.addDependency(bomDep);
        bomDep = new Dependency();
        bomDep.setGroupId("io.quarkus");
        bomDep.setArtifactId("quarkus-integration-test-class-transformer-deployment");
        bomDep.setVersion(quarkusCoreVersion());
        dm.addDependency(bomDep);

        for (String test : member.config.tests) {
            generateIntegrationTestModule(test, pom);
        }
        persistPom(pom);
    }

    private void generateIntegrationTestModule(String test, Model parentPom) throws MojoExecutionException {
        final ArtifactCoords testArtifact = ArtifactCoords.fromString(test);

        final String moduleName = testArtifact.getArtifactId();

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");
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

        Dependency dep = new Dependency();
        dep.setGroupId(testArtifact.getGroupId());
        dep.setArtifactId(testArtifact.getArtifactId());
        if (!testArtifact.getClassifier().isEmpty()) {
            dep.setClassifier(testArtifact.getClassifier());
        }
        dep.setType(testArtifact.getType());
        dep.setVersion(testArtifact.getVersion());
        pom.addDependency(dep);

        dep = new Dependency();
        dep.setGroupId(testArtifact.getGroupId());
        dep.setArtifactId(testArtifact.getArtifactId());
        dep.setClassifier("tests");
        dep.setType("test-jar");
        dep.setVersion(testArtifact.getVersion());
        dep.setScope("test");
        pom.addDependency(dep);

        final Build build = new Build();
        pom.setBuild(build);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-surefire-plugin");

        Xpp3Dom config = new Xpp3Dom("configuration");
        plugin.setConfiguration(config);
        final Xpp3Dom depsToScan = new Xpp3Dom("dependenciesToScan");
        config.addChild(depsToScan);
        final Xpp3Dom testDep = new Xpp3Dom("dependency");
        depsToScan.addChild(testDep);
        testDep.setValue(testArtifact.getGroupId() + ":" + testArtifact.getArtifactId());

        final Path pomFile;
        try {
            pomFile = nonWorkspaceResolver().resolve(toPomArtifact(testArtifact)).getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve " + toPomArtifact(testArtifact), e);
        } catch (MojoExecutionException e) {
            throw e;
        }

        final Model testModel;
        try {
            testModel = ModelUtils.readModel(pomFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + pomFile, e);
        }

        for (Dependency d : testModel.getDependencies()) {
            if ("test".equals(d.getScope())) {
                pom.addDependency(d);
            }
        }

        // NATIVE
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
        plugin = new Plugin();
        buildBase.addPlugin(plugin);
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-failsafe-plugin");
        plugin.setConfiguration(config);
        PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.addGoal("integration-test");
        exec.addGoal("verify");

        config = new Xpp3Dom("configuration");
        exec.setConfiguration(config);
        final Xpp3Dom sysProps = new Xpp3Dom("systemProperties");
        config.addChild(sysProps);
        final Xpp3Dom nativeImagePath = new Xpp3Dom("native.image.path");
        sysProps.addChild(nativeImagePath);
        nativeImagePath.setValue("${project.build.directory}/${project.build.finalName}-runner");

        plugin = new Plugin();
        buildBase.addPlugin(plugin);
        plugin.setGroupId("io.quarkus");
        plugin.setArtifactId("quarkus-maven-plugin");
        plugin.setVersion(quarkusCoreVersion());
        plugin.setConfiguration(config);
        exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setId("native-image");
        exec.addGoal("build");

        config = new Xpp3Dom("configuration");
        exec.setConfiguration(config);
        final Xpp3Dom appArtifact = new Xpp3Dom("appArtifact");
        config.addChild(appArtifact);
        appArtifact.setValue(testArtifact.toString());

        persistPom(pom);

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

    private void generateMainPlatformModule(Model parentPom) throws MojoExecutionException {
        final String moduleName = "main";
        final Artifact bomArtifact = getMainBomArtifact();

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");
        pom.setArtifactId(getArtifactIdBase(parentPom) + moduleName + "-parent");
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
                pom, false);

        // to make the descriptor pom resolvable during the platform BOM generation, we need to persist the generated POMs
        persistPom(pom);
        persistPom(parentPom);
        generateAllInclusivePlatformBomModule(pom);

        if (platformConfig.generatePlatformProperties) {
            final PlatformMemberConfig tmpConfig = new PlatformMemberConfig();
            tmpConfig.bom = platformConfig.bom;
            final PlatformMember tmp = new PlatformMember(tmpConfig);
            tmp.baseModel = pom;
            generatePlatformPropertiesModule(tmp, false);
        }

        if (platformConfig.skipInstall) {
            skipInstall(pom);
        }
        persistPom(pom);
    }

    private void generatePlatformDescriptorModule(ArtifactCoords descriptorCoords, Model parentPom,
            boolean addPlatformReleaseConfig)
            throws MojoExecutionException {
        final String moduleName = "descriptor";
        parentPom.addModule(moduleName);
        final Path moduleDir = parentPom.getProjectDirectory().toPath().resolve(moduleName);

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");

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

        final Build build = new Build();
        pom.setBuild(build);
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
        e.setValue(quarkusCoreVersion());
        config.addChild(e);

        if (addPlatformReleaseConfig && platformConfig.platformRelease != null) {
            final Xpp3Dom stackConfig = new Xpp3Dom("platformRelease");
            config.addChild(stackConfig);
            e = new Xpp3Dom("platformKey");
            e.setValue(platformConfig.platformRelease.platformKey);
            stackConfig.addChild(e);
            e = new Xpp3Dom("stream");
            e.setValue(platformConfig.platformRelease.stream);
            stackConfig.addChild(e);
            e = new Xpp3Dom("version");
            e.setValue(platformConfig.platformRelease.version);
            stackConfig.addChild(e);
            final Xpp3Dom membersConfig = new Xpp3Dom("members");
            stackConfig.addChild(membersConfig);
            for (PlatformMember m : members.values()) {
                e = new Xpp3Dom("member");
                e.setValue(m.stackDescriptorCoords().toString());
                membersConfig.addChild(e);
            }
        }

        String overridesFile = null;
        if (descriptorCoords.getArtifactId().equals("quarkus-bom-quarkus-platform-descriptor")) {
            // copy the quarkus-bom metadata
            JsonNode metadata = null;
            final Artifact bom = quarkusCore.originalBomCoords();
            final Path jsonPath = artifactResolver().resolve(new DefaultArtifact(bom.getGroupId(),
                    bom.getArtifactId() + Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX, bom.getVersion(), "json",
                    bom.getVersion())).getArtifact().getFile().toPath();
            try (BufferedReader reader = Files.newBufferedReader(jsonPath)) {
                JsonNode node = JsonCatalogMapperHelper.mapper().readTree(reader);
                metadata = node.get("metadata");
            } catch (IOException e1) {
                throw new MojoExecutionException("Failed to deserialize " + jsonPath, e1);
            }
            if (metadata != null) {
                final Path overridesJson = moduleDir.resolve("src").resolve("main").resolve("resources")
                        .resolve("metadata.json");
                final ObjectNode root = JsonCatalogMapperHelper.mapper().createObjectNode();
                root.set("metadata", metadata);
                try {
                    JsonCatalogMapperHelper.serialize(root, overridesJson);
                } catch (Exception ex) {
                    throw new MojoExecutionException("Failed to serialize metadata to " + overridesJson, ex);
                }
                overridesFile = overridesJson.toString();
            }
        }

        if (overridesFile != null
                || platformConfig.descriptorGenerator != null && platformConfig.descriptorGenerator.overridesFile != null) {
            e = new Xpp3Dom("overridesFile");
            if (overridesFile == null) {
                overridesFile = platformConfig.descriptorGenerator.overridesFile;
            } else if (platformConfig.descriptorGenerator.overridesFile != null) {
                overridesFile += "," + platformConfig.descriptorGenerator.overridesFile;
            }
            e.setValue(overridesFile);
            config.addChild(e);
        }
        if (platformConfig.descriptorGenerator != null && platformConfig.descriptorGenerator.skipCategoryCheck) {
            e = new Xpp3Dom("skipCategoryCheck");
            e.setValue("true");
            config.addChild(e);
            plugin.setConfiguration(config);
        }
        if (platformConfig.descriptorGenerator != null && platformConfig.descriptorGenerator.resolveDependencyManagement) {
            e = new Xpp3Dom("resolveDependencyManagement");
            e.setValue("true");
            config.addChild(e);
            plugin.setConfiguration(config);
        }

        final Dependency dep = new Dependency();
        dep.setGroupId(descriptorCoords.getGroupId());
        dep.setArtifactId(bomArtifact);
        dep.setType("pom");
        dep.setVersion(descriptorCoords.getVersion());
        pom.addDependency(dep);

        final Path pomXml = moduleDir.resolve("pom.xml");
        pom.setPomFile(pomXml.toFile());
        persistPom(pom);
    }

    private void generatePlatformPropertiesModule(PlatformMember member, boolean addPlatformReleaseConfig)
            throws MojoExecutionException {

        final ArtifactCoords propertiesCoords = member.propertiesCoords();
        final Model parentPom = member.baseModel;

        final String moduleName = "properties";
        parentPom.addModule(moduleName);
        final Path moduleDir = parentPom.getProjectDirectory().toPath().resolve(moduleName);

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");

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
        bom.setVersion(propertiesCoords.getVersion());
        bom.setType("pom");
        bom.setScope("import");

        final Build build = new Build();
        pom.setBuild(build);
        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-resources-plugin");
        PluginExecution exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("process-resources");
        exec.addGoal("resources");

        plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId(pluginDescriptor().getGroupId());
        plugin.setArtifactId(pluginDescriptor().getArtifactId());
        exec = new PluginExecution();
        plugin.addExecution(exec);
        exec.setPhase("process-resources");
        exec.addGoal("platform-properties");

        final Xpp3Dom config = new Xpp3Dom("configuration");
        final Properties props = new Properties();

        if (addPlatformReleaseConfig && platformConfig.platformRelease != null) {
            final Xpp3Dom stackConfig = new Xpp3Dom("platformRelease");
            config.addChild(stackConfig);
            Xpp3Dom e = new Xpp3Dom("platformKey");
            e.setValue(platformConfig.platformRelease.platformKey);
            stackConfig.addChild(e);
            e = new Xpp3Dom("stream");
            e.setValue(platformConfig.platformRelease.stream);
            stackConfig.addChild(e);
            e = new Xpp3Dom("version");
            e.setValue(platformConfig.platformRelease.version);
            stackConfig.addChild(e);
            final Xpp3Dom membersConfig = new Xpp3Dom("members");
            stackConfig.addChild(membersConfig);
            final Iterator<PlatformMember> i = members.values().iterator();
            final StringBuilder buf = new StringBuilder();
            while (i.hasNext()) {
                final PlatformMember m = i.next();
                e = new Xpp3Dom("member");
                membersConfig.addChild(e);
                e.setValue(m.stackDescriptorCoords().toString());
                buf.append(PlatformArtifacts.ensureBomArtifact(m.stackDescriptorCoords()));
                if (i.hasNext()) {
                    buf.append(",");
                }
            }

            props.setProperty(
                    "platform.release-info@" + platformConfig.platformRelease.platformKey + "$"
                            + platformConfig.platformRelease.stream + "#" + platformConfig.platformRelease.version,
                    buf.toString());
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

        if (member.config.bom != null) {
            // this is just to copy the core properties to the main platform
            final PlatformMember srcMember = platformConfig.bom.equals(member.config.bom) ? quarkusCore : member;
            List<org.eclipse.aether.graph.Dependency> originalDm;
            try {
                originalDm = nonWorkspaceResolver().resolveDescriptor(srcMember.originalBomCoords()).getManagedDependencies();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to resolve " + member.originalBomCoords(), e);
            }
            for (org.eclipse.aether.graph.Dependency d : originalDm) {
                final Artifact a = d.getArtifact();
                if (a.getExtension().equals("properties")
                        && a.getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)
                        && a.getArtifactId().startsWith(srcMember.originalBomCoords().getArtifactId())
                        && a.getGroupId().equals(srcMember.originalBomCoords().getGroupId())
                        && a.getVersion().equals(srcMember.originalBomCoords().getVersion())) {
                    try (BufferedReader reader = Files
                            .newBufferedReader(nonWorkspaceResolver.resolve(a).getArtifact().getFile().toPath())) {
                        props.load(reader);
                    } catch (Exception e) {
                        throw new MojoExecutionException("Failed to resolve " + a, e);
                    }
                    break;
                }
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("platform-properties.properties"))) {
            props.store(writer, pom.getName());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist " + dir.resolve("platform-properties.properties"), e);
        }
    }

    private String quarkusCoreVersion() {
        return quarkusCore.originalBomCoords().getVersion();
    }

    private PluginDescriptor pluginDescriptor() {
        return pluginDescr == null ? pluginDescr = (PluginDescriptor) getPluginContext().get("pluginDescriptor") : pluginDescr;
    }

    private void generateAllInclusivePlatformBomModule(Model parentPom) throws MojoExecutionException {

        final Artifact bomArtifact = getMainBomArtifact();
        final PlatformBomConfig.Builder configBuilder = PlatformBomConfig.builder()
                .pomResolver(PomSource.of(bomArtifact))
                .includePlatformProperties(platformConfig.generatePlatformProperties)
                .platformBom(bomArtifact)
                .enableNonMemberQuarkiverseExtensions(platformConfig.bomGenerator.enableNonMemberQuarkiverseExtensions);

        for (PlatformMember member : members.values()) {
            configBuilder.importBom(member.bomGeneratorMemberConfig());
        }

        if (platformConfig.bomGenerator != null && platformConfig.bomGenerator.enforcedDependencies != null) {
            for (String enforced : platformConfig.bomGenerator.enforcedDependencies) {
                final AppArtifactCoords coords = AppArtifact.fromString(enforced);
                configBuilder.enforce(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                        coords.getType(), coords.getVersion()));
            }
        }
        if (platformConfig.bomGenerator != null && platformConfig.bomGenerator.excludedDependencies != null) {
            for (String excluded : platformConfig.bomGenerator.excludedDependencies) {
                configBuilder.exclude(AppArtifactKey.fromString(excluded));
            }
        }
        if (platformConfig.bomGenerator != null && platformConfig.bomGenerator.excludedGroups != null) {
            for (String excluded : platformConfig.bomGenerator.excludedGroups) {
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
        mainGeneratedBom = bomComposer.platformBom();

        final Model baseModel = project.getModel().clone();
        baseModel.setName(getNameBase(parentPom) + " Quarkus Platform BOM");

        final String moduleName = "bom";
        parentPom.addModule(moduleName);
        mainPlatformBomXml = parentPom.getProjectDirectory().toPath().resolve(moduleName).resolve("pom.xml");
        try {
            PlatformBomUtils.toPom(mainGeneratedBom, mainPlatformBomXml, baseModel, catalogResolver());
        } catch (MojoExecutionException e) {
            throw e;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist generated BOM to " + mainPlatformBomXml, e);
        }

        quarkusCore.originalBom = bomComposer.originalQuarkusCoreBom();
        quarkusCore.generatedBom = bomComposer.generatedQuarkusCoreBom();

        for (DecomposedBom importedBom : bomComposer.alignedMemberBoms()) {
            final PlatformMember member = members.get(toKey(importedBom.bomArtifact()));
            member.originalBom = bomComposer.originalMemberBom(
                    member.originalBomCoords == null ? member.generatedBomCoords() : member.originalBomCoords);
            member.generatedBom = importedBom;
        }
    }

    private Artifact getMainBomArtifact() {
        return mainBom == null ? mainBom = toPomArtifact(platformConfig.bom) : mainBom;
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

        public boolean skipDeploy;
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

        PlatformMember(PlatformMemberConfig config) {
            this.config = config;
            if (config.bom == null) {
                originalBomCoords = null;
                if (config.dependencyManagement.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Neither BOM coordinates nor dependencyManagement have been configured for member " + config.name);
                }
            } else {
                if (!config.dependencyManagement.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Either BOM or dependencyManagement are allowed for a platform member: " + config.name);
                }
                originalBomCoords = toPomArtifact(config.bom);
            }
        }

        Artifact originalBomCoords() {
            return originalBomCoords;
        }

        Artifact generatedBomCoords() {
            if (generatedBomCoords == null) {
                if (config.release == null || config.release.upcoming == null) {
                    generatedBomCoords = new DefaultArtifact(getMainBomArtifact().getGroupId(),
                            originalBomCoords().getArtifactId(), null,
                            "pom", originalBomCoords().getVersion());
                } else {
                    generatedBomCoords = toPomArtifact(config.release.upcoming);
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
                final List<org.eclipse.aether.graph.Dependency> dm = new ArrayList<>(config.dependencyManagement.size());
                for (String coordsStr : config.dependencyManagement) {
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
            String currentCoords = skipDeploy ? config.release.previous : config.release.upcoming;
            if (currentCoords == null) {
                currentCoords = config.release.upcoming;
            }
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

    private void persistPom(final Model pom) throws MojoExecutionException {
        try {
            pom.getPomFile().getParentFile().mkdirs();
            ModelUtils.persistModel(pom.getPomFile().toPath(), pom);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate the platform BOM " + pom.getPomFile(), e);
        }
    }

    private static String getArtifactIdBase(Model pom) {
        final String s = pom.getArtifactId();
        final int i = s.lastIndexOf('-');
        return i > 0 ? s.substring(0, i + 1) : s;
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
