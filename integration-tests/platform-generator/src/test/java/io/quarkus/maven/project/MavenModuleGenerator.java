package io.quarkus.maven.project;

import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Scm;

public class MavenModuleGenerator {

    public static final String DEFAULT_GROUP_ID = "org.acme";
    public static final String DEFAULT_VERSION = "1.0-SNAPSHOT";

    public static MavenModuleGenerator newProject(String groupId, String artifactId, String version) {
        return new MavenModuleGenerator(groupId, artifactId, version);
    }

    public static MavenModuleGenerator newMultiModuleProject(String groupId, String artifactId, String version) {
        return newProject(groupId, artifactId, version).setPackaging(ArtifactCoords.TYPE_POM);
    }

    public static MavenModuleGenerator newMultiModuleProject(String artifactId) {
        return newMultiModuleProject(DEFAULT_GROUP_ID, artifactId, DEFAULT_VERSION);
    }

    private final MavenModuleGenerator parent;
    private final Model model;
    private Map<String, MavenModuleGenerator> modules = Map.of();
    private List<MavenPluginBuilder> plugins = List.of();
    private List<MavenPluginBuilder> managedPlugins = List.of();
    private List<Consumer<Path>> postGenerate = List.of();

    MavenModuleGenerator(String groupId, String artifactId, String version) {
        parent = null;
        model = newModel();
        model.setGroupId(Objects.requireNonNull(groupId, "groupId is null"));
        model.setArtifactId(Objects.requireNonNull(artifactId, "artifactId is null"));
        model.setVersion(Objects.requireNonNull(version, "version is null"));
    }

    MavenModuleGenerator(MavenModuleGenerator parent, String moduleName) {
        this.parent = parent;
        model = newModel();

        final Parent parentModel = new Parent();
        parentModel.setGroupId(parent.getGroupId());
        parentModel.setArtifactId(parent.getArtifactId());
        parentModel.setVersion(parent.getVersion());
        model.setParent(parentModel);

        model.setArtifactId(Objects.requireNonNull(moduleName, "moduleName is null"));
    }

    public MavenModuleGenerator getParent() {
        return parent;
    }

    public MavenModuleGenerator setPackaging(String packaging) {
        model.setPackaging(packaging);
        return this;
    }

    public MavenModuleGenerator setGroupId(String groupId) {
        model.setGroupId(groupId);
        return this;
    }

    public MavenModuleGenerator setArtifactId(String artifactId) {
        model.setArtifactId(artifactId);
        return this;
    }

    public MavenModuleGenerator setVersion(String version) {
        model.setVersion(version);
        return this;
    }

    public MavenModuleGenerator setProperty(String name, String value) {
        model.getProperties().setProperty(name, value);
        return this;
    }

    public MavenModuleGenerator setScm(String repoUrl, String tag) {
        var scm = model.getScm();
        if (scm == null) {
            scm = new Scm();
            model.setScm(scm);
        }
        scm.setConnection(repoUrl);
        scm.setTag(tag);
        return this;
    }

    public MavenModuleGenerator setScmTag(String tag) {
        var scm = model.getScm();
        if (scm == null) {
            scm = new Scm();
            model.setScm(scm);
        }
        scm.setTag(tag);
        return this;
    }

    public MavenModuleGenerator addModule(String moduleName) {
        Objects.requireNonNull(moduleName, "module name is null");
        model.addModule(moduleName);
        var m = new MavenModuleGenerator(this, moduleName);
        if (modules.isEmpty()) {
            modules = new HashMap<>();
        }
        modules.put(moduleName, m);
        return m;
    }

    public MavenModuleGenerator addQuarkusExtensionRuntimeModule(String moduleName) {
        final MavenModuleGenerator m = addModule(moduleName);
        return m.addPostGenerateTask(moduleDir -> {
            try {
                var props = new Properties();
                props.setProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT,
                        m.getGroupId() + ":" + m.getArtifactId() + "-deployment:" + m.getVersion());
                var metaInf = Files.createDirectories(
                        moduleDir.resolve("src").resolve("main").resolve("resources").resolve("META-INF"));
                try (BufferedWriter writer = Files
                        .newBufferedWriter(metaInf.resolve(BootstrapConstants.DESCRIPTOR_FILE_NAME))) {
                    props.store(writer, "generated");
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public MavenModuleGenerator addPomModule(String moduleName) {
        return addModule(moduleName).setPackaging(ArtifactCoords.TYPE_POM);
    }

    public MavenModuleGenerator addVersionConstraint(MavenModuleGenerator module) {
        return addVersionConstraint(module.getGroupId(), module.getArtifactId(), module.getVersion());
    }

    public MavenModuleGenerator addVersionConstraint(String artifactId) {
        return addVersionConstraint("${project.groupId}", artifactId, "${project.version}");
    }

    public MavenModuleGenerator addVersionConstraint(String groupId, String artifactId, String version) {
        return addVersionConstraint(groupId, artifactId, null, null, version);
    }

    public MavenModuleGenerator addVersionConstraint(String groupId, String artifactId, String classifier, String type,
            String version) {
        var d = new Dependency();
        d.setGroupId(groupId);
        d.setArtifactId(artifactId);
        if (classifier != null && !classifier.isEmpty()) {
            d.setClassifier(classifier);
        }
        if (type != null && !type.equals(ArtifactCoords.TYPE_JAR)) {
            d.setType(type);
        }
        d.setVersion(version);
        var dm = model.getDependencyManagement();
        if (dm == null) {
            dm = new DependencyManagement();
            model.setDependencyManagement(dm);
        }
        dm.addDependency(d);
        return this;
    }

    public MavenModuleGenerator importBom(MavenModuleGenerator module) {
        return importBom(module.getGroupId(), module.getArtifactId(), module.getVersion());
    }

    public MavenModuleGenerator importBom(String artifactId) {
        return importBom("${project.groupId}", artifactId, "${project.version}");
    }

    public MavenModuleGenerator importBom(String groupId, String artifactId, String version) {
        var d = new Dependency();
        d.setGroupId(groupId);
        d.setArtifactId(artifactId);
        d.setVersion(version);
        d.setType(ArtifactCoords.TYPE_POM);
        d.setScope("import");
        var dm = model.getDependencyManagement();
        if (dm == null) {
            dm = new DependencyManagement();
            model.setDependencyManagement(dm);
        }
        dm.addDependency(d);
        return this;
    }

    public MavenModuleGenerator addDependency(MavenModuleGenerator module) {
        addDependency(module.getGroupId(), module.getArtifactId(), module.getVersion());
        return this;
    }

    public MavenModuleGenerator addDependency(String artifactId) {
        return addDependency("${project.groupId}", artifactId, "${project.version}");
    }

    public MavenModuleGenerator addDependency(String groupId, String artifactId, String version) {
        var d = new Dependency();
        d.setGroupId(groupId);
        d.setArtifactId(artifactId);
        d.setVersion(version);
        model.addDependency(d);
        return this;
    }

    public MavenModuleGenerator addManagedDependency(MavenModuleGenerator module) {
        return addManagedDependency(module.getGroupId(), module.getArtifactId());
    }

    public MavenModuleGenerator addManagedDependency(String artifactId) {
        return addManagedDependency("${project.groupId}", artifactId);
    }

    public MavenModuleGenerator addManagedDependency(String groupId, String artifactId) {
        var d = new Dependency();
        d.setGroupId(groupId);
        d.setArtifactId(artifactId);
        model.addDependency(d);
        return this;
    }

    public MavenPluginBuilder addManagedPlugin(String groupId, String artifactId) {
        return addPlugin(groupId, artifactId, null);
    }

    public MavenPluginBuilder addPlugin(String groupId, String artifactId, String version) {
        var pl = new MavenPluginBuilder(groupId, artifactId, version);
        if (plugins.isEmpty()) {
            plugins = new ArrayList<>();
        }
        plugins.add(pl);
        return pl;
    }

    public MavenPluginBuilder managePlugin(String groupId, String artifactId) {
        return managePlugin(groupId, artifactId, null);
    }

    public MavenPluginBuilder managePlugin(String groupId, String artifactId, String version) {
        var pl = new MavenPluginBuilder(groupId, artifactId, version);
        if (managedPlugins.isEmpty()) {
            managedPlugins = new ArrayList<>();
        }
        managedPlugins.add(pl);
        return pl;
    }

    public MavenModuleGenerator addPostGenerateTask(Consumer<Path> task) {
        if (postGenerate.isEmpty()) {
            postGenerate = new ArrayList<>();
        }
        postGenerate.add(task);
        return this;
    }

    public String getGroupId() {
        var groupId = model.getGroupId();
        if (groupId == null) {
            var parent = model.getParent();
            if (parent != null) {
                return parent.getGroupId();
            }
        }
        return groupId;
    }

    public String getArtifactId() {
        return model.getArtifactId();
    }

    public String getVersion() {
        var version = model.getVersion();
        if (version == null) {
            var parent = model.getParent();
            if (parent != null) {
                return parent.getVersion();
            }
        }
        return version;
    }

    public ArtifactCoords getArtifactCoords() {
        if (ArtifactCoords.TYPE_POM.equals(model.getPackaging())) {
            return ArtifactCoords.pom(getGroupId(), getArtifactId(), getVersion());
        }
        return ArtifactCoords.jar(getGroupId(), getArtifactId(), getVersion());
    }

    public void generate(Path dir) {

        final Path pomFile = dir.resolve("pom.xml");

        if (!modules.isEmpty()) {
            for (var moduleName : model.getModules()) {
                var module = modules.get(moduleName);
                if (module == null) {
                    throw new IllegalStateException("Failed to locate module " + moduleName);
                }
                var moduleDir = dir.resolve(moduleName);

                final Parent parent = model.getParent();
                if (parent != null) {
                    parent.setRelativePath(moduleDir.relativize(pomFile).toString());
                }

                if (module.model.getArtifactId() == null) {
                    module.model.setArtifactId(moduleName);
                }

                module.generate(moduleDir);
            }
        }

        if (!plugins.isEmpty()) {
            var build = model.getBuild();
            if (build == null) {
                build = new Build();
                model.setBuild(build);
            }
            for (var plugin : plugins) {
                build.addPlugin(plugin.build());
            }
        }

        if (!managedPlugins.isEmpty()) {
            var build = model.getBuild();
            if (build == null) {
                build = new Build();
                model.setBuild(build);
            }
            var pm = build.getPluginManagement();
            if (pm == null) {
                pm = new PluginManagement();
                build.setPluginManagement(pm);
            }
            for (var plugin : managedPlugins) {
                pm.addPlugin(plugin.build());
            }
        }

        try {
            Files.createDirectories(dir);
            ModelUtils.persistModel(pomFile, model);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist POM " + pomFile, e);
        }

        for (var task : postGenerate) {
            task.accept(dir);
        }
    }

    private static Model newModel() {
        var model = new Model();
        model.setModelVersion("4.0.0");
        return model;
    }
}
