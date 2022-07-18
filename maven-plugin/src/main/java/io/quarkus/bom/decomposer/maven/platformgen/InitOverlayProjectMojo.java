package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Mojo(name = "init-overlay-project", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE, requiresProject = false)
public class InitOverlayProjectMojo extends AbstractMojo {

    @Parameter(required = true, defaultValue = "com.redhat.quarkus.platform", property = "groupId")
    String groupId;

    @Parameter(required = false, property = "artifactId")
    String artifactId;

    @Parameter(required = false, property = "version")
    String version;

    @Parameter(required = true, defaultValue = "quarkusio")
    String githubOrg;

    @Parameter(required = true, defaultValue = "quarkus-platform")
    String githubRepo;

    @Parameter(required = false, property = "forTag")
    String forTag;

    @Parameter(required = false, property = "forBranch")
    String forBranch;

    @Parameter(required = false)
    Set<String> excludeResources = Set.of(".git", "generated-platform-project");

    @Parameter(required = true, defaultValue = "${basedir}", property = "dir")
    File dir;
    private Path projectDir;

    @Parameter(required = false, property = "forceOverlay")
    boolean forceOverlay;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        projectDir = dir.toPath().normalize().toAbsolutePath();
        if (Files.exists(projectDir)) {
            if (!Files.isDirectory(projectDir)) {
                throw new MojoExecutionException(
                        "Can't create a new project at " + projectDir + " because it appears to be an existing file");
            }
            final boolean emptyDir;
            try (Stream<Path> stream = Files.list(projectDir)) {
                emptyDir = stream.count() == 0;
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read the content of directory " + projectDir, e);
            }
            if (!emptyDir) {
                if (!forceOverlay) {
                    throw new MojoExecutionException("The project directory " + projectDir
                            + " isn't empty. If you still want to re-initialize the overly please add -DforceOverlay to the command line");
                }
                getLog().warn("Overriding the existing overlay in " + projectDir);
            }
        }

        run(projectDir.getParent(), "git", "clone", "https://github.com/" + githubOrg + "/" + githubRepo + ".git",
                projectDir.getFileName().toString());
        String contentRef = getContentRef();
        if (!"main".equals(contentRef)) {
            run(projectDir, "git", "checkout", contentRef);
        }

        try (Stream<Path> stream = Files.list(projectDir)) {
            final Iterator<Path> i = stream.iterator();
            while (i.hasNext()) {
                final Path p = i.next();
                if (excludeResources.contains(projectDir.relativize(p).toString())) {
                    IoUtils.recursiveDelete(p);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to process " + projectDir, e);
        }

        final Path pomXml = projectDir.resolve("pom.xml");
        if (!Files.exists(pomXml)) {
            throw new MojoExecutionException("Failed to locate " + pomXml);
        }

        final Model originalModel;
        try {
            originalModel = ModelUtils.readModel(pomXml);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + pomXml, e);
        }

        final Model model = new Model();
        model.setModelVersion(originalModel.getModelVersion());
        final Parent parent = new Parent();
        parent.setGroupId(ModelUtils.getGroupId(originalModel));
        parent.setArtifactId(originalModel.getArtifactId());
        parent.setVersion(ModelUtils.getVersion(originalModel));
        parent.setRelativePath("");
        model.setParent(parent);

        model.setPackaging("pom");
        model.setGroupId(groupId);
        model.setArtifactId(artifactId == null ? originalModel.getArtifactId() : artifactId);
        if (version != null && !version.isBlank()) {
            model.setVersion(version);
        }

        model.getProperties().setProperty("quarkus.upstream.version", originalModel.getVersion());

        final Build build = new Build();
        model.setBuild(build);
        build.setPlugins(originalModel.getBuild().getPlugins());

        for (Plugin plugin : build.getPlugins()) {
            if (plugin.getArtifactId().equals("quarkus-platform-bom-maven-plugin")) {
                configurePlatform(plugin);
                break;
            }
        }

        try {
            ModelUtils.persistModel(pomXml, model);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist POM to " + pomXml, e);
        }
    }

    private static void configurePlatform(Plugin plugin) throws MojoExecutionException {
        Object o = plugin.getConfiguration();
        Xpp3Dom config;
        if (o != null) {
            if (!(o instanceof Xpp3Dom)) {
                throw new MojoExecutionException(
                        "Plugin configuration is not of type " + Xpp3Dom.class.getName() + " but " + o.getClass().getName());
            }
            config = (Xpp3Dom) o;
        } else {
            config = new Xpp3Dom("configuration");
            plugin.setConfiguration(config);
        }
        Xpp3Dom platform = new Xpp3Dom("platformConfig");
        config.addChild(platform);

        var upstreamQuarkusCoreVersion = new Xpp3Dom("upstreamQuarkusCoreVersion");
        upstreamQuarkusCoreVersion.setValue("${quarkus.upstream.version}");
        platform.addChild(upstreamQuarkusCoreVersion);
    }

    private void run(Path workDir, String... commands) throws MojoExecutionException {
        Process process = null;
        try {
            process = new ProcessBuilder()
                    .command(commands)
                    .redirectErrorStream(true)
                    .inheritIO()
                    .directory(workDir.toFile())
                    .start();
            process.waitFor();
            process = null;
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to clone", e);
        } finally {
            if (process != null) {
                final Process p = process;
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        p.destroy();
                        try {
                            p.waitFor();
                        } catch (InterruptedException e) {
                            getLog().warn("Unable to properly wait for dev-mode end", e);
                        }
                    }
                }, "Git " + commands[1] + " shutdown hook"));
            }
        }
    }

    private String getContentRef() {
        if (forTag != null && !forTag.isBlank()) {
            return forTag;
        }
        if (forBranch != null && !forBranch.isBlank()) {
            return forBranch;
        }
        return "main";
    }
}
