package io.quarkus.domino.test.repo;

import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Scm;
import org.eclipse.aether.util.artifact.JavaScopes;

public class TestModule {

    private final TestProject project;
    private final boolean root;
    private ArtifactCoords parentPom;
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging = ArtifactCoords.TYPE_JAR;
    private List<TestModule> modules = List.of();
    private Map<String, List<ArtifactCoords>> dependenciesByScope = Map.of();
    private Map<String, List<ArtifactCoords>> constraintsByScope = Map.of();

    TestModule(TestProject project, String artifactId) {
        this.project = Objects.requireNonNull(project);
        this.root = true;
        this.groupId = project.getGroupId();
        this.version = project.getVersion();
        this.artifactId = artifactId;
    }

    private TestModule(TestModule parentPom, String artifactId) {
        Objects.requireNonNull(parentPom);
        root = false;
        this.parentPom = ArtifactCoords.pom(parentPom.getGroupId(), parentPom.getArtifactId(), parentPom.getVersion());
        this.project = parentPom.getProject();
        this.artifactId = artifactId;
    }

    public TestProject getProject() {
        return project;
    }

    public ArtifactCoords getParentPom() {
        return parentPom;
    }

    public TestModule setParentPom(ArtifactCoords parentPom) {
        this.parentPom = parentPom;
        return this;
    }

    public String getGroupId() {
        if (groupId != null) {
            return groupId;
        }
        if (parentPom != null) {
            return parentPom.getGroupId();
        }
        return project.getGroupId();
    }

    public TestModule setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public TestModule setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public String getVersion() {
        if (version != null) {
            return version;
        }
        if (parentPom != null) {
            return parentPom.getVersion();
        }
        return project.getVersion();
    }

    public TestModule setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getPackaging() {
        return packaging;
    }

    public TestModule setPackaging(String packaging) {
        this.packaging = packaging;
        return this;
    }

    public Collection<TestModule> getModules() {
        return modules;
    }

    public TestModule addModule(String artifactId) {
        var module = new TestModule(this, artifactId);
        if (modules.isEmpty()) {
            modules = new ArrayList<>();
        }
        modules.add(module);
        return module;
    }

    public TestModule addPomModule(String artifactId) {
        return addModule(artifactId).setPackaging(ArtifactCoords.TYPE_POM);
    }

    public TestModule addDependency(String artifactId) {
        return addDependency(ArtifactCoords.jar(getGroupId(), artifactId, getVersion()), JavaScopes.COMPILE);
    }

    public TestModule addDependency(TestModule module) {
        return addDependency(ArtifactCoords.jar(module.getGroupId(), module.getArtifactId(), module.getVersion()),
                JavaScopes.COMPILE);
    }

    public TestModule addDependency(String groupId, String artifactId, String version) {
        return addDependency(ArtifactCoords.jar(groupId, artifactId, version), JavaScopes.COMPILE);
    }

    public TestModule addDependency(ArtifactCoords coords, String scope) {
        if (dependenciesByScope.isEmpty()) {
            dependenciesByScope = new LinkedHashMap<>();
        }
        dependenciesByScope.computeIfAbsent(scope, k -> new ArrayList<>()).add(coords);
        return this;
    }

    public TestModule addManagedDependency(String groupId, String artifactId) {
        return addDependency(ArtifactCoords.jar(groupId, artifactId, null), JavaScopes.COMPILE);
    }

    public TestModule addManagedDependency(TestModule module) {
        return addDependency(ArtifactCoords.jar(module.getGroupId(), module.getArtifactId(), null), JavaScopes.COMPILE);
    }

    public TestModule addVersionConstraint(String artifactId) {
        return addVersionConstraint(ArtifactCoords.jar(getGroupId(), artifactId, getVersion()), JavaScopes.COMPILE);
    }

    public TestModule addVersionConstraint(TestModule module) {
        return addVersionConstraint(ArtifactCoords.jar(module.getGroupId(), module.getArtifactId(), module.getVersion()),
                JavaScopes.COMPILE);
    }

    public TestModule addVersionConstraint(String groupId, String artifactId, String version) {
        return addVersionConstraint(ArtifactCoords.jar(groupId, artifactId, version), JavaScopes.COMPILE);
    }

    public TestModule addVersionConstraint(ArtifactCoords coords, String scope) {
        if (constraintsByScope.isEmpty()) {
            constraintsByScope = new LinkedHashMap<>();
        }
        constraintsByScope.computeIfAbsent(scope, k -> new ArrayList<>()).add(coords);
        return this;
    }

    public TestModule importBom(TestModule module) {
        return importBom(module.getGroupId(), module.getArtifactId(), module.getVersion());
    }

    public TestModule importBom(String artifactId) {
        return addVersionConstraint(ArtifactCoords.pom(getGroupId(), artifactId, getVersion()), "import");
    }

    public TestModule importBom(String groupId, String artifactId, String version) {
        return addVersionConstraint(ArtifactCoords.pom(groupId, artifactId, version), "import");
    }

    public Model getModel() {
        var model = new Model();
        model.setModelVersion("4.0.0");
        if (parentPom != null) {
            var parent = new Parent();
            parent.setGroupId(parentPom.getGroupId());
            parent.setArtifactId(parentPom.getArtifactId());
            parent.setVersion(parentPom.getVersion());
            model.setParent(parent);
        }
        if (groupId != null) {
            model.setGroupId(groupId);
        }
        model.setArtifactId(artifactId);
        if (version != null) {
            model.setVersion(version);
        }
        model.setPackaging(packaging);

        for (var module : modules) {
            model.addModule(module.getArtifactId());
        }

        if (!constraintsByScope.isEmpty()) {
            var dm = new DependencyManagement();
            dm.setDependencies(toModelDeps(constraintsByScope));
            model.setDependencyManagement(dm);
        }
        if (!dependenciesByScope.isEmpty()) {
            model.setDependencies(toModelDeps(dependenciesByScope));
        }

        if (root && project.getRepoUrl() != null) {
            var scm = new Scm();
            scm.setConnection(project.getRepoUrl());
            scm.setTag(project.getTag());
            model.setScm(scm);
        }
        return model;
    }

    private static List<Dependency> toModelDeps(Map<String, List<ArtifactCoords>> deps) {
        var result = new ArrayList<Dependency>();
        for (Map.Entry<String, List<ArtifactCoords>> scopeDeps : deps.entrySet()) {
            var scope = JavaScopes.COMPILE.equals(scopeDeps.getKey()) ? null : scopeDeps.getKey();
            for (ArtifactCoords coords : scopeDeps.getValue()) {
                var d = new Dependency();
                d.setGroupId(coords.getGroupId());
                d.setArtifactId(coords.getArtifactId());
                if (coords.getType() != null && !ArtifactCoords.TYPE_JAR.equals(coords.getType())) {
                    d.setType(coords.getType());
                }
                if (!coords.getClassifier().isEmpty()) {
                    d.setClassifier(coords.getClassifier());
                }
                if (coords.getVersion() != null && !coords.getVersion().isEmpty()) {
                    d.setVersion(coords.getVersion());
                }
                if (scope != null) {
                    d.setScope(scope);
                }
                result.add(d);
            }
        }
        return result;
    }
}
