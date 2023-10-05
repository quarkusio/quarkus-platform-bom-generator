package io.quarkus.domino.processor;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.ReleaseRepo;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

public class ParallelExecution {

    private static final String AUTOBUILD_SUFFIX = "-autobuild-";
    private static final int MIN_BUILD_NUMBER = 1;
    private static final int MAX_BUILD_NUMBER = 99999;
    private static final char[] PROJECT_NAME_SEPARATORS = { '/', ':', '?', '=' };

    public static void main(String[] args) throws Exception {

        final Path workDir = Path.of("target").resolve("parallel-build").normalize().toAbsolutePath();
        IoUtils.recursiveDelete(workDir);
        final Path configFile = generateConfig(workDir.resolve("dependency-config.yaml"));
        ParallelExecution pe = new ParallelExecution(workDir);
        pe.run(configFile);
    }

    private static Path generateConfig(Path configFile) throws Exception {
        ProjectDependencyConfig.builder()
                .setProjectBom(ArtifactCoords.pom("io.vertx", "vertx-dependencies", "4.3.4"))
                .setExcludeGroupIds(Set.of(
                        "org.junit.platform",
                        "org.junit.jupiter"))
                .setWarnOnResolutionErrors(true)
                .setLogCodeRepos(true)
                .setLogArtifactsToBuild(true)
                .setLogModulesToBuild(true)
                .setLogSummary(true)
                .persist(configFile);
        return configFile;
    }

    private final Path localMavenRepo;
    private final Path projects;
    private MavenArtifactResolver resolver;
    private final Map<ScmRevision, ProjectInfo> projectInfos = new HashMap<>();
    private final Map<ArtifactCoords, ProjectInfo> artifactProjects = new HashMap<>();

    private final AtomicInteger projectsBuilt = new AtomicInteger();
    private final AtomicInteger projectsBuilding = new AtomicInteger();
    private final AtomicInteger projectsRemaining = new AtomicInteger();

    ParallelExecution(Path workDir) throws Exception {
        this.localMavenRepo = IoUtils.mkdirs(workDir.resolve("maven-repo"));
        this.projects = IoUtils.mkdirs(workDir.resolve("projects"));
    }

    void run(Path configFile) throws Exception {
        run(ProjectDependencyConfig.fromFile(configFile));
    }

    void run(ProjectDependencyConfig config) throws Exception {

        resolver = MavenArtifactResolver.builder().build();

        final Collection<ReleaseRepo> allRepos = ProjectDependencyResolver.builder()
                .setDependencyConfig(config)
                .setArtifactResolver(resolver)
                .build()
                .getSortedReleaseRepos();

        generateProjectSources(allRepos);

        projectsRemaining.set(allRepos.size());
        final long startTime = System.currentTimeMillis();
        buildInParallel(allRepos);
        //buildInSequence(allRepos);
        log(String.format("Built %s artifacts from %s repositories in %.2f sec", artifactProjects.size(), allRepos.size(),
                (double) (System.currentTimeMillis() - startTime) / 1000));
    }

    private void buildInSequence(final Collection<ReleaseRepo> allRepos) throws Exception {
        final Map<String, String> versionProps = new HashMap<>();
        for (ReleaseRepo repo : allRepos) {
            ProjectInfo project = this.projectInfos.get(repo.getRevision());
            final String version = project.originalVersion + AUTOBUILD_SUFFIX + generateBuildNumber();

            final List<String> command = new ArrayList<>();
            command.add("mvn");
            command.add("install");
            command.add("-Drevision=" + version);
            for (String prop : project.getBuildProperties()) {
                final String value = versionProps.get(prop);
                if (value == null) {
                    throw new IllegalStateException("Version property " + prop + " is not available");
                }
                command.add("-D" + prop + "=" + value);
            }
            command.add("-Dmaven.repo.local=" + this.localMavenRepo);

            Process process = null;
            try {
                final ProcessBuilder processBuilder = new ProcessBuilder(command)
                        .redirectOutput(project.dir.resolve("build.log").toFile())
                        .redirectErrorStream(true)
                        .directory(project.dir.toFile());

                projectsRemaining.decrementAndGet();
                projectsBuilding.incrementAndGet();
                log(getProgressPrefix().append("launching build ").append(project.name).append(" ").append(version));
                process = processBuilder.start();
                if (process.waitFor() != 0) {
                    throw new IllegalStateException("Failed building " + project.name + "-" + version);
                }
                projectsBuilding.decrementAndGet();
                projectsBuilt.incrementAndGet();
                log(getProgressPrefix().append("finished building ").append(project.name).append(" ").append(version));
                versionProps.put(project.getVersionProperty(), version);
            } finally {
                if (process != null) {
                    process.destroy();
                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void buildInParallel(final Collection<ReleaseRepo> allRepos) {
        final ParallelTreeProcessor<ScmRevision, ReleaseRepo, Map<String, String>> treeProcessor = ParallelTreeProcessor
                .with(new NodeProcessor<ScmRevision, ReleaseRepo, Map<String, String>>() {

                    @Override
                    public ScmRevision getNodeId(ReleaseRepo node) {
                        return node.getRevision();
                    }

                    @Override
                    public Iterable<ReleaseRepo> getChildren(ReleaseRepo node) {
                        return node.getDependencies();
                    }

                    @Override
                    public Function<ExecutionContext<ScmRevision, ReleaseRepo, Map<String, String>>, TaskResult<ScmRevision, ReleaseRepo, Map<String, String>>> createFunction() {
                        return ctx -> {
                            final ProjectInfo project = projectInfos.get(ctx.getNode().getRevision());
                            final String version = project.originalVersion + AUTOBUILD_SUFFIX + generateBuildNumber();

                            final List<String> command = new ArrayList<>();
                            command.add("mvn");
                            command.add("install");
                            command.add("-Drevision=" + version);
                            for (ScmRevision dep : ctx.getDependencies()) {
                                final TaskResult<ScmRevision, ReleaseRepo, Map<String, String>> result = ctx
                                        .getDependencyResult(dep);
                                if (result.isCanceled() || result.isFailure()) {
                                    return ctx.canceled(Map.of());
                                }
                                for (Map.Entry<String, String> prop : result.getOutcome().entrySet()) {
                                    command.add("-D" + prop.getKey() + "=" + prop.getValue());
                                }
                            }
                            command.add("-Dmaven.repo.local=" + localMavenRepo);

                            Process process = null;
                            try {
                                final ProcessBuilder processBuilder = new ProcessBuilder(command)
                                        .redirectOutput(project.dir.resolve("build.log").toFile())
                                        .redirectErrorStream(true)
                                        .directory(project.dir.toFile());

                                projectsRemaining.decrementAndGet();
                                projectsBuilding.incrementAndGet();
                                log(getProgressPrefix().append("launching build ").append(project.name).append(" ")
                                        .append(version));
                                process = processBuilder.start();
                                if (process.waitFor() != 0) {
                                    log("Failed building " + project.name + "-" + version);
                                    return ctx.failure(Map.of());
                                }
                                projectsBuilding.decrementAndGet();
                                projectsBuilt.incrementAndGet();
                                log(getProgressPrefix().append("finished building ").append(project.name).append(" ")
                                        .append(version));
                            } catch (Exception e) {
                                e.printStackTrace();
                                return ctx.failure(Map.of());
                            } finally {
                                if (process != null) {
                                    process.destroy();
                                    try {
                                        process.waitFor();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            return ctx.success(Map.of(project.getVersionProperty(), version));
                        };
                    }
                });

        for (ReleaseRepo r : allRepos) {
            if (r.isRoot()) {
                treeProcessor.addRoot(r);
            }
        }

        final List<TaskResult<ScmRevision, ReleaseRepo, Map<String, String>>> results = treeProcessor.schedule().join();
    }

    private void generateProjectSources(Collection<ReleaseRepo> releaseRepos) throws Exception {
        final Map<ScmRepository, String> projectNames = new HashMap<>();
        for (ReleaseRepo repo : releaseRepos) {
            initProjectInfo(repo, projectNames);
        }

        for (ProjectInfo r : projectInfos.values()) {
            generateProject(r);
        }
    }

    private void initProjectInfo(ReleaseRepo repo, Map<ScmRepository, String> projectNames) {
        final String projectName = deriveProjectName(repo.getRevision().getRepository(), projectNames);
        final String projectVersion = repo.getArtifacts().keySet().iterator().next().getVersion();
        final Path projectDir = IoUtils.mkdirs(projects.resolve(projectName + "-" + projectVersion));
        final ProjectInfo info = new ProjectInfo(repo, projectVersion, projectDir, projectName);
        projectInfos.put(info.release.getRevision(), info);

        for (ArtifactCoords c : repo.getArtifacts().keySet()) {
            if (!c.getVersion().equals(info.originalVersion)) {
                log("WARN: inconsistent versioning detected in " + repo.getRevision());
            }
            if (c.getType().equals(ArtifactCoords.TYPE_JAR) || c.getType().equals(ArtifactCoords.TYPE_POM)) {
                artifactProjects.put(c, info);
            } else {
                log("ERROR: unsupported artifact type " + c);
            }
        }
    }

    private void generateProject(ProjectInfo project)
            throws Exception {

        log("Generating project: " + project.id);

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");
        pom.setVersion("${revision}");

        final Collection<ArtifactCoords> artifacts = project.release.getArtifacts().keySet();
        if (artifacts.size() == 1) {
            final ArtifactCoords a = artifacts.iterator().next();
            pom.setGroupId(a.getGroupId());
            pom.setArtifactId(a.getArtifactId());
            pom.setPackaging(a.getType());
            configureDependencies(project, pom, a);
        } else {
            String groupId = null;
            ArtifactCoords parentPom = null;
            for (ArtifactCoords d : artifacts) {
                groupId = d.getGroupId();
                if (d.getArtifactId().endsWith("-parent")) {
                    parentPom = d;
                    break;
                }
            }

            pom.setGroupId(groupId);
            pom.setArtifactId(parentPom == null ? project.name + "-parent" : parentPom.getArtifactId());
            pom.setPackaging(ArtifactCoords.TYPE_POM);
            final Map<String, Model> addedModules = new HashMap<>();
            for (ArtifactCoords d : artifacts) {
                if (parentPom != null && d.getArtifactId().equals(parentPom.getArtifactId())) {
                    continue;
                }
                final String moduleName = d.getArtifactId();
                Model moduleModel = addedModules.get(moduleName);
                if (moduleModel == null) {
                    pom.addModule(moduleName);
                    moduleModel = generateModule(project, pom, IoUtils.mkdirs(project.dir.resolve(moduleName)), d);
                    addedModules.put(moduleName, moduleModel);
                }
                if (!d.getClassifier().isEmpty()) {
                    Build build = moduleModel.getBuild();
                    if (build == null) {
                        build = new Build();
                        moduleModel.setBuild(build);
                    }
                    Plugin jarPlugin = null;
                    for (Plugin pl : build.getPlugins()) {
                        if (pl.getArtifactId().equals("maven-jar-plugin")) {
                            jarPlugin = pl;
                            break;
                        }
                    }
                    if (jarPlugin == null) {
                        jarPlugin = new Plugin();
                        jarPlugin.setArtifactId("maven-jar-plugin");
                        jarPlugin.setVersion("3.3.0");
                        PluginExecution e = new PluginExecution();
                        e.setId("default-jar");
                        e.setGoals(List.of("jar"));
                        jarPlugin.addExecution(e);
                        build.addPlugin(jarPlugin);
                    }
                    PluginExecution e = new PluginExecution();
                    e.setId(d.getClassifier() + "-jar");
                    e.setGoals(List.of("jar"));
                    final Xpp3Dom config = new Xpp3Dom("configuration");
                    e.setConfiguration(config);
                    Xpp3Dom classifierDom = new Xpp3Dom("classifier");
                    classifierDom.setValue(d.getClassifier());
                    config.addChild(classifierDom);
                    jarPlugin.addExecution(e);

                    ModelUtils.persistModel(project.dir.resolve(moduleName).resolve("pom.xml"), moduleModel);
                }
            }
        }
        configureFlattenPlugin(pom);
        ModelUtils.persistModel(project.dir.resolve("pom.xml"), pom);
    }

    private Model generateModule(ProjectInfo project, Model parentPom, Path moduleDir, ArtifactCoords artifact)
            throws Exception {

        final Model pom = new Model();
        pom.setModelVersion("4.0.0");

        Parent parent = new Parent();
        parent.setGroupId(parentPom.getGroupId());
        parent.setArtifactId(parentPom.getArtifactId());
        parent.setVersion(parentPom.getVersion());
        pom.setParent(parent);

        if (!parentPom.getGroupId().equals(artifact.getGroupId())) {
            pom.setGroupId(artifact.getGroupId());
        }
        pom.setArtifactId(artifact.getArtifactId());

        if (artifact.getType().equals(ArtifactCoords.TYPE_POM)) {
            pom.setPackaging(ArtifactCoords.TYPE_POM);
        }

        configureDependencies(project, pom, artifact);
        ModelUtils.persistModel(moduleDir.resolve("pom.xml"), pom);
        return pom;
    }

    private void configureDependencies(ProjectInfo project, Model pom, ArtifactCoords moduleArtifact)
            throws BootstrapMavenException {
        for (Dependency directDep : resolver.resolveDescriptor(new DefaultArtifact(moduleArtifact.getGroupId(),
                moduleArtifact.getArtifactId(), moduleArtifact.getClassifier(), moduleArtifact.getType(),
                moduleArtifact.getVersion()))
                .getDependencies()) {
            final Artifact depArtifact = directDep.getArtifact();
            final ProjectInfo projectDep = artifactProjects
                    .get(ArtifactCoords.of(depArtifact.getGroupId(), depArtifact.getArtifactId(), depArtifact.getClassifier(),
                            depArtifact.getExtension(), depArtifact.getVersion()));
            if (projectDep != null) {
                final String versionProp;
                if (projectDep != project) {
                    versionProp = projectDep.getVersionProperty();
                    if (!pom.getProperties().containsKey(versionProp)) {
                        pom.getProperties().setProperty(versionProp, "placeholder");
                        project.addBuildProperty(versionProp);
                    }
                } else {
                    versionProp = "project.version";
                }
                org.apache.maven.model.Dependency modelDep = new org.apache.maven.model.Dependency();
                modelDep.setGroupId(depArtifact.getGroupId());
                modelDep.setArtifactId(depArtifact.getArtifactId());
                modelDep.setVersion("${" + versionProp + "}");
                if (!depArtifact.getExtension().equals(ArtifactCoords.TYPE_JAR)) {
                    modelDep.setType(depArtifact.getExtension());
                }
                if (!depArtifact.getClassifier().isEmpty()) {
                    modelDep.setClassifier(depArtifact.getClassifier());
                }
                pom.addDependency(modelDep);
            }
        }
    }

    private void configureFlattenPlugin(Model pom) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }

        Plugin plugin = new Plugin();
        build.addPlugin(plugin);
        plugin.setGroupId("org.codehaus.mojo");
        plugin.setArtifactId("flatten-maven-plugin");
        plugin.setVersion("1.1.0");
        Xpp3Dom config = new Xpp3Dom("configuration");
        plugin.setConfiguration(config);
        Xpp3Dom updatePomFile = new Xpp3Dom("updatePomFile");
        updatePomFile.setValue("true");
        config.addChild(updatePomFile);
        Xpp3Dom flattenMode = new Xpp3Dom("flattenMode");
        flattenMode.setValue("resolveCiFriendliesOnly");
        config.addChild(flattenMode);

        Xpp3Dom pomElements = new Xpp3Dom("pomElements");
        config.addChild(pomElements);
        Xpp3Dom deps = new Xpp3Dom("dependencies");
        deps.setValue("flatten");
        pomElements.addChild(deps);

        PluginExecution e = new PluginExecution();
        e.setId("flatten");
        e.setPhase("process-resources");
        e.addGoal("flatten");
        plugin.addExecution(e);

        e = new PluginExecution();
        e.setId("flatten.clean");
        e.setPhase("clean");
        e.addGoal("clean");
        plugin.addExecution(e);
    }

    private static String deriveProjectName(ScmRepository o, Map<ScmRepository, String> projectNames) {
        String name = projectNames.get(o);
        if (name != null) {
            return name;
        }
        final String[] split = o.toString().split("/");
        int j = split.length - 1;
        if (split[j].isEmpty()) {
            --j;
        }
        if (j <= 0) {
            name = split[0];
        } else {
            name = split[j - 1];
            if (!name.isEmpty()) {
                name += "-";
            }
            name += split[j];
        }

        if (name.contains("${")) {
            name = name.replace("${", "");
            name = name.replace("}", "");
            name = name.replace("env.", "");
            name = name.toLowerCase();
        }
        for (int i = 0; i < PROJECT_NAME_SEPARATORS.length; ++i) {
            name = name.replace(PROJECT_NAME_SEPARATORS[i], '-');
        }
        if (projectNames.values().contains(name)) {
            log("WARN: duplicate project name " + name + " for " + o.toString());
        }
        projectNames.put(o, name);
        return name;
    }

    private StringBuilder getProgressPrefix() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[Remaining: ").append(projectsRemaining.get());
        sb.append(", in progress: ").append(projectsBuilding.get());
        sb.append(", done: ").append(projectsBuilt.get()).append("] ");
        return sb;
    }

    private static class ProjectInfo {
        final String id;
        final String name;
        final String originalVersion;
        final Path dir;
        final ReleaseRepo release;
        final Set<String> buildProperties = new HashSet<>();

        ProjectInfo(ReleaseRepo release, String originalVersion, Path dir, String name) {
            this.id = name + "-" + originalVersion;
            this.originalVersion = originalVersion;
            this.release = release;
            this.dir = dir;
            this.name = name;
        }

        String getVersionProperty() {
            return id + ".version";
        }

        void addBuildProperty(String name) {
            this.buildProperties.add(name);
        }

        Collection<String> getBuildProperties() {
            return buildProperties;
        }
    }

    private static String generateBuildNumber() {
        final int n = (int) (Math.random() * (MAX_BUILD_NUMBER - MIN_BUILD_NUMBER + 1) + MIN_BUILD_NUMBER);
        return String.format("%05d", n);
    }

    private static void log(Object o) {
        System.out.println(o == null ? "null" : o.toString());
    }
}
