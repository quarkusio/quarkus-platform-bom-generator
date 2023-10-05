package io.quarkus.domino.cli;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalWorkspace;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.ProjectDependencyResolver;
import io.quarkus.domino.ReleaseRepo;
import io.quarkus.domino.manifest.ManifestGenerator;
import io.quarkus.domino.processor.ExecutionContext;
import io.quarkus.domino.processor.NodeProcessor;
import io.quarkus.domino.processor.ParallelTreeProcessor;
import io.quarkus.domino.processor.TaskResult;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.jgit.api.Git;
import picocli.CommandLine;

@CommandLine.Command(name = "build")
public class Build extends BaseDepsToBuildCommand {

    private static final String JAVA8_HOME = "JAVA8_HOME";
    private static final String DOMINO = "-domino-";
    private static final int MIN_BUILD_NUMBER = 1;
    private static final int MAX_BUILD_NUMBER = 99999;

    @CommandLine.Option(names = { "--local-maven-repo" }, description = "Local Maven repo to use for builds")
    public File localMavenRepo;

    @CommandLine.Option(names = { "--manifest" }, description = "Generate an SBOM", defaultValue = "false")
    public boolean manifest;

    @Override
    protected Integer process(ProjectDependencyResolver depResolver) {

        final Path workDir = Path.of("target").resolve("build").normalize().toAbsolutePath();
        final Path projectsDir = workDir.resolve("projects");
        final Path localMavenRepo = this.localMavenRepo == null ? workDir.resolve("local-maven-repo")
                : this.localMavenRepo.toPath();

        if (Files.exists(projectsDir)) {
            log("Cleaning " + projectsDir);
            IoUtils.recursiveDelete(projectsDir);
        } else {
            IoUtils.mkdirs(projectsDir);
        }

        if (Files.exists(localMavenRepo)) {
            log("Cleaning " + localMavenRepo + " from *" + DOMINO + "* artifacts");
            try {
                Files.walkFileTree(localMavenRepo, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        if (dir.getFileName().toString().contains(DOMINO)) {
                            IoUtils.recursiveDelete(dir);
                            log("  deleted " + dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to clean repository " + localMavenRepo, e);
            }
        } else {
            IoUtils.mkdirs(localMavenRepo);
        }

        final Function<Collection<ReleaseRepo>, List<TaskResult<ScmRevision, ReleaseRepo, BuildResult>>> func = repos -> {

            final ParallelTreeProcessor<ScmRevision, ReleaseRepo, BuildResult> treeProcessor = ParallelTreeProcessor
                    .with(new NodeProcessor<>() {

                        @Override
                        public ScmRevision getNodeId(ReleaseRepo node) {
                            return node.getRevision();
                        }

                        @Override
                        public Iterable<ReleaseRepo> getChildren(ReleaseRepo node) {
                            return node.getDependencies();
                        }

                        @Override
                        public Function<ExecutionContext<ScmRevision, ReleaseRepo, BuildResult>, TaskResult<ScmRevision, ReleaseRepo, BuildResult>> createFunction() {
                            return ctx -> {
                                try {
                                    final Path projectDir = cloneRepo(ctx.getNode(), projectsDir, ctx);
                                    final BuildResult buildResult = reversionMavenProject(projectDir, ctx);

                                    final List<String> command = new ArrayList<>();
                                    command.add("mvn");
                                    command.add("install");
                                    command.add("-Dmaven.repo.local=" + localMavenRepo);
                                    if (ctx.getId().origin().toString().contains("slf4j")) {
                                        command.add("-DskipTests");
                                    } else {
                                        command.add("-Dmaven.test.skip");
                                    }
                                    command.add("-Drat.skip");
                                    command.add("-Danimal.sniffer.skip");
                                    command.add("-Dmaven.javadoc.skip");

                                    Process process = null;
                                    try {
                                        final ProcessBuilder processBuilder = new ProcessBuilder(command)
                                                .redirectOutput(projectDir.resolve("build.log").toFile())
                                                .redirectErrorStream(true)
                                                .directory(projectDir.toFile());

                                        final StringBuilder sb = new StringBuilder();
                                        sb.append("Building ").append(projectDir);
                                        if (buildResult.java8) {
                                            final String java8Home = System.getenv().get(JAVA8_HOME);
                                            if (java8Home == null) {
                                                throw new RuntimeException(
                                                        projectDir + " requires Java 8 but " + JAVA8_HOME + " isn't set");
                                            }
                                            processBuilder.environment().put("JAVA_HOME", java8Home);
                                            sb.append(" with Java 8");
                                        }
                                        log(sb);
                                        process = processBuilder.start();
                                        if (process.waitFor() != 0) {
                                            log("Failed building " + projectDir);
                                            return ctx.failure(buildResult);
                                        }
                                        log("Finished building " + projectDir);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return ctx.failure(buildResult);
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
                                    return ctx.success(buildResult);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    throw e;
                                }
                            };
                        }
                    });

            for (ReleaseRepo r : repos) {
                if (r.isRoot()) {
                    treeProcessor.addRoot(r);
                }
            }
            return treeProcessor.schedule().join();
        };

        final List<TaskResult<ScmRevision, ReleaseRepo, BuildResult>> results = depResolver.applyToSorted(func);
        boolean failure = false;
        final Map<ArtifactKey, List<String>> builtVersions = new HashMap<>();
        for (TaskResult<ScmRevision, ReleaseRepo, BuildResult> r : results) {
            final StringBuilder sb = new StringBuilder();
            sb.append(getBuildStatus(r)).append(" building ").append(r.getId());
            if (r.isFailure()) {
                failure = true;
                if (r.getOutcome() != null) {
                    sb.append(" (see ").append(r.getOutcome().projectDir.resolve("build.log")).append(" for details)");
                }
            }
            if (!failure && r.getOutcome() != null) {
                r.getOutcome().reversioned.forEach((k, v) -> builtVersions.computeIfAbsent(k, key -> new ArrayList<>()).add(v));
            }
            log(sb);
        }

        if (!failure && manifest) {
            final ProjectDependencyConfig originalConfig = depResolver.getConfig();
            final ProjectDependencyConfig.Mutable newConfig = ProjectDependencyConfig.builder();
            newConfig.setExcludeBomImports(originalConfig.isExcludeBomImports());
            originalConfig.getExcludePatterns().forEach(newConfig::addExcludePattern);
            newConfig.setExcludeParentPoms(originalConfig.isExcludeParentPoms());
            newConfig.setIncludeAlreadyBuilt(true);
            originalConfig.getIncludePatterns().forEach(newConfig::addIncludePattern);
            newConfig.setIncludeNonManaged(originalConfig.isIncludeNonManaged());
            newConfig.setLevel(originalConfig.getLevel());
            newConfig.setLogArtifactsToBuild(originalConfig.isLogArtifactsToBuild());
            newConfig.setLogCodeRepos(originalConfig.isLogCodeRepos());
            newConfig.setLogModulesToBuild(originalConfig.isLogModulesToBuild());
            newConfig.setLogNonManagedVisited(originalConfig.isLogNonManagedVisitied());
            newConfig.setLogRemaining(originalConfig.isLogRemaining());
            newConfig.setLogSummary(originalConfig.isLogSummary());
            newConfig.setLogTrees(originalConfig.isLogTrees());
            newConfig.setWarnOnResolutionErrors(originalConfig.isWarnOnResolutionErrors());

            if (!originalConfig.getProjectArtifacts().isEmpty()) {
                newConfig.setProjectArtifacts(originalConfig.getProjectArtifacts().stream()
                        .map(c -> getBuiltVersion(c, builtVersions)).collect(Collectors.toList()));
            }
            if (originalConfig.getProjectBom() != null) {
                newConfig.setProjectBom(getBuiltVersion(originalConfig.getProjectBom(), builtVersions));
            }
            if (!originalConfig.getIncludeArtifacts().isEmpty()) {
                newConfig.setIncludeArtifacts(originalConfig.getIncludeArtifacts().stream()
                        .map(c -> getBuiltVersion(c, builtVersions)).collect(Collectors.toSet()));
            }

            try {
                final MavenArtifactResolver artifactResolver = MavenArtifactResolver.builder()
                        .setWorkspaceDiscovery(false)
                        .setLocalRepository(localMavenRepo.toString())
                        .build();
                ProjectDependencyResolver.builder()
                        .setDependencyConfig(newConfig)
                        .setArtifactResolver(artifactResolver)
                        .build()
                        .consumeSorted(ManifestGenerator.builder()
                                .setArtifactResolver(artifactResolver)
                                .build().toConsumer());
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to generate the report", e);
            }
        }

        return CommandLine.ExitCode.OK;
    }

    private static ArtifactCoords getBuiltVersion(ArtifactCoords original, Map<ArtifactKey, List<String>> builtVersions) {
        final List<String> versions = builtVersions.get(ArtifactKey.ga(original.getGroupId(), original.getArtifactId()));
        if (versions == null) {
            return original;
        }
        for (String version : versions) {
            if (version.startsWith(original.getVersion())) {
                return ArtifactCoords.of(original.getGroupId(), original.getArtifactId(), original.getClassifier(),
                        original.getType(), version);
            }
        }
        return original;
    }

    private static String getBuildStatus(TaskResult<?, ?, ?> r) {
        if (r.isSuccess()) {
            return "SUCCESS";
        }
        if (r.isCanceled()) {
            return "CANCELED";
        }
        if (r.isSkipped()) {
            return "SKIPPED";
        }
        if (r.isFailure()) {
            return "FAILURE";
        }
        throw new RuntimeException("Unexpected build status for " + r.getId());
    }

    private Path cloneRepo(ReleaseRepo release, Path codeReposDir,
            ExecutionContext<ScmRevision, ReleaseRepo, BuildResult> ctx) {
        log("Cloning " + release.getRevision());
        final String url = release.getRevision().origin().toString();
        int i = url.lastIndexOf('/');
        Path projectDir = codeReposDir;
        if (i >= 0) {
            projectDir = codeReposDir.resolve(url.substring(i + 1));
        }
        projectDir = projectDir.resolve(release.getRevision().getValue().replace('/', '_'));
        try (Git git = Git.cloneRepository()
                .setDirectory(projectDir.toFile())
                .setURI(url)
                .setBranch(release.getRevision().getValue())
                .call()) {
        } catch (Exception e) {
            Map.Entry<ArtifactCoords, List<RemoteRepository>> artifact;
            if (release.getArtifacts().size() == 1
                    && (artifact = release.getArtifacts().entrySet().iterator().next()).getKey().getType()
                            .equals(ArtifactCoords.TYPE_POM)) {
                // possibly a parent POM for which the repo couldn't be resolved
                final ArtifactCoords pomCoords = artifact.getKey();
                final Path pom;
                try {
                    pom = getArtifactResolver().resolve(new DefaultArtifact(pomCoords.getGroupId(), pomCoords.getArtifactId(),
                            pomCoords.getClassifier(), pomCoords.getType(), pomCoords.getVersion()), artifact.getValue())
                            .getArtifact().getFile().toPath();
                } catch (BootstrapMavenException e1) {
                    ctx.failure(e1);
                    throw new RuntimeException("Failed to resolve " + pomCoords, e1);
                }
                try {
                    IoUtils.copy(pom, projectDir.resolve("pom.xml"));
                } catch (IOException e1) {
                    ctx.failure(e1);
                    throw new RuntimeException("Failed to copy " + pom + " to " + projectDir.resolve("pom.xml"), e1);
                }
            } else {
                ctx.failure(e);
                throw new RuntimeException("Failed to clone " + url, e);
            }
        }
        log("Cloned " + release.getRevision() + " to " + projectDir);
        if (!Files.exists(projectDir.resolve("pom.xml"))) {
            final RuntimeException e = new RuntimeException(release + " cloned to " + projectDir + " is missing pom.xml");
            ctx.failure(e);
            throw e;
        }
        return projectDir;
    }

    private static BuildResult reversionMavenProject(Path projectDir,
            ExecutionContext<ScmRevision, ReleaseRepo, BuildResult> ctx) {

        final Map<ArtifactKey, String> reversionedDeps = new HashMap<>();
        for (ScmRevision id : ctx.getDependencies()) {
            reversionedDeps.putAll(ctx.getDependencyResult(id).getOutcome().reversioned);
        }

        final LocalProject rootProject;
        try {
            rootProject = new BootstrapMavenContext(
                    BootstrapMavenContext.config()
                            .setCurrentProject(projectDir.toString())
                            .setEffectiveModelBuilder(true))
                                    .getCurrentProject();
        } catch (BootstrapMavenException e) {
            ctx.failure(e);
            throw new RuntimeException("Failed to initialize Maven context for " + projectDir, e);
        }

        final String originalVersion = rootProject.getRawModel().getVersion();
        if (originalVersion == null) {
            RuntimeException e = new RuntimeException(
                    "The project version is missing in " + rootProject.getRawModel().getPomFile());
            ctx.failure(e);
            throw e;
        }
        final String newVersion = originalVersion + DOMINO + generateBuildNumber();
        log("Reversioning " + projectDir + " to " + newVersion);
        final LocalWorkspace ws = rootProject.getWorkspace();
        final Map<ArtifactKey, String> reversioned = new HashMap<>(ws.getProjects().size());
        boolean java8 = false;
        for (LocalProject project : ws.getProjects().values()) {
            final Model model = project.getRawModel();
            if (model.getVersion() != null) {
                if (!model.getVersion().equals(originalVersion)) {
                    throw new RuntimeException("Module " + model.getPomFile() + " has version " + model.getVersion()
                            + " instead of " + originalVersion);
                }
                model.setVersion(newVersion);
            }
            reversioned.put(project.getKey(), newVersion);
            final Parent parentModel = model.getParent();
            if (parentModel != null) {
                final ArtifactKey ga = ArtifactKey.ga(parentModel.getGroupId(), parentModel.getArtifactId());
                if (ws.getProject(ga) == null) {
                    final String newParentVersion = reversionedDeps.get(ga);
                    if (newParentVersion != null) {
                        parentModel.setVersion(newParentVersion);
                    }
                } else {
                    parentModel.setVersion(newVersion);
                }
            }
            if (model.getDependencyManagement() != null) {
                for (Dependency d : model.getDependencyManagement().getDependencies()) {
                    reversionDependency(d, originalVersion, newVersion, project, reversionedDeps, ctx);
                }
            }
            for (Dependency d : model.getDependencies()) {
                reversionDependency(d, originalVersion, newVersion, project, reversionedDeps, ctx);
            }

            final Model effectiveModel = project.getModelBuildingResult().getEffectiveModel();
            if (!java8) {
                String javaCompilerSource = effectiveModel.getProperties().getProperty("java.compiler.source");
                if (javaCompilerSource != null) {
                    java8 = isJava8(javaCompilerSource);
                }
                if (!java8) {
                    javaCompilerSource = effectiveModel.getProperties().getProperty("maven.compiler.source");
                    if (javaCompilerSource != null) {
                        java8 = isJava8(javaCompilerSource);
                    }
                }
            }
            if (effectiveModel.getBuild() != null) {
                for (Plugin plugin : effectiveModel.getBuild().getPlugins()) {
                    if (!java8 && plugin.getArtifactId().equals("maven-compiler-plugin")) {
                        final Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
                        if (config != null) {
                            final Xpp3Dom source = config.getChild("source");
                            if (source != null) {
                                java8 = isJava8(source.getValue());
                            }
                        }
                    } else if (plugin.getArtifactId().equals("maven-bundle-plugin")
                            && plugin.getVersion().startsWith("2.4.0-build")) {
                        if (model.getBuild() != null) {
                            for (Plugin p : model.getBuild().getPlugins()) {
                                if (p.getArtifactId().equals("maven-bundle-plugin")) {
                                    p.setVersion("2.4.0");
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            boolean jacksonGradleComment = false;
            if (effectiveModel.getGroupId().startsWith("com.fasterxml.jackson")) {
                try {
                    jacksonGradleComment = Files.readString(model.getPomFile().toPath())
                            .contains("<!-- do_not_remove: published-with-gradle-metadata -->");
                } catch (IOException e) {
                    ctx.failure(e);
                    throw new RuntimeException("Failed to read " + model.getPomFile(), e);
                }
            }

            try {
                ModelUtils.persistModel(model.getPomFile().toPath(), model);
            } catch (IOException e) {
                ctx.failure(e);
                throw new RuntimeException("Failed to persist " + model.getPomFile(), e);
            }

            if (jacksonGradleComment) {
                final List<String> allLines;
                try {
                    allLines = Files.readAllLines(model.getPomFile().toPath());
                } catch (IOException e) {
                    ctx.failure(e);
                    throw new RuntimeException("Failed to read " + model.getPomFile(), e);
                }
                try (BufferedWriter writer = Files.newBufferedWriter(model.getPomFile().toPath())) {
                    writer.write(allLines.get(0));
                    writer.newLine();
                    writer.write("<!-- do_not_remove: published-with-gradle-metadata -->");
                    writer.newLine();
                    for (int i = 1; i < allLines.size(); ++i) {
                        writer.write(allLines.get(i));
                        writer.newLine();
                    }
                } catch (IOException e) {
                    ctx.failure(e);
                    throw new RuntimeException("Failed to persist " + model.getPomFile(), e);
                }
            }
        }
        return new BuildResult(projectDir, reversioned, java8);
    }

    private static boolean isJava8(String javaStr) {
        if (javaStr.startsWith("1.")) {
            javaStr = javaStr.substring(2);
        }
        if (Integer.parseInt(javaStr) <= 8) {
            return true;
        }
        return false;
    }

    private static void reversionDependency(Dependency d, String expectedProjectVersion, String newProjectVersion,
            LocalProject project, Map<ArtifactKey, String> reversionedDeps,
            ExecutionContext<ScmRevision, ReleaseRepo, BuildResult> ctx) {
        String groupId = d.getGroupId();
        if ("${project.groupId}".equals(groupId)) {
            groupId = ModelUtils.getGroupId(project.getRawModel());
        }
        final ArtifactKey ga = ArtifactKey.ga(groupId, d.getArtifactId());
        if (project.getWorkspace().getProject(ga) == null) {
            final String newDepVersion = reversionedDeps.get(ga);
            if (newDepVersion != null) {
                d.setVersion(newDepVersion);
            }
            return;
        }
        if (d.getVersion() != null) {
            if (!d.getVersion().equals("${project.version}")) {
                if (!d.getVersion().equals(expectedProjectVersion)) {
                    RuntimeException e = new RuntimeException(
                            d + " in " + project.getRawModel().getPomFile() + " does not have version "
                                    + expectedProjectVersion);
                    ctx.failure(e);
                    throw e;
                }
                d.setVersion(newProjectVersion);
            } else if ("test-jar".equals(d.getType())
                    || "tests".equals(d.getClassifier())) {
                d.setVersion(expectedProjectVersion);
            }
        }
    }

    private static String generateBuildNumber() {
        final int n = (int) (Math.random() * (MAX_BUILD_NUMBER - MIN_BUILD_NUMBER + 1) + MIN_BUILD_NUMBER);
        return String.format("%05d", n);
    }

    private static void log(Object o) {
        System.out.println(o == null ? "null" : o.toString());
    }

    private static class BuildResult {
        final Path projectDir;
        final Map<ArtifactKey, String> reversioned;
        final boolean java8;

        private BuildResult(Path projectDir, Map<ArtifactKey, String> reversioned, boolean java8) {
            this.projectDir = projectDir;
            this.reversioned = reversioned;
            this.java8 = java8;
        }
    }
}
