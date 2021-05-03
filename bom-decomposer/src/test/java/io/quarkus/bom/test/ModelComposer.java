package io.quarkus.bom.test;

import io.quarkus.bootstrap.model.AppArtifactCoords;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;

public class ModelComposer<T extends ModelComposer<T>> {

    private final Model model = new Model();

    public ModelComposer(String coordsStr) {
        this();
        final AppArtifactCoords coords = AppArtifactCoords.fromString(coordsStr);
        groupId(coords.getGroupId());
        artifactId(coords.getArtifactId());
        version(coords.getVersion());
        packaging(coords.getType());
    }

    public ModelComposer() {
        model.setModelVersion("4.0.0");
    }

    @SuppressWarnings("unchecked")
    public T groupId(String groupId) {
        model.setGroupId(groupId);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T artifactId(String artifactId) {
        model.setArtifactId(artifactId);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T version(String version) {
        model.setVersion(version);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T packaging(String packaging) {
        model.setPackaging(packaging);
        return (T) this;
    }

    public T managedArtifactId(String artifactId) {
        final Dependency d = new Dependency();
        d.setGroupId(model.getGroupId());
        d.setArtifactId(artifactId);
        d.setVersion(model.getVersion());
        return managedDep(d);
    }

    public T managedDep(String coordsStr) {
        final AppArtifactCoords coords = AppArtifactCoords.fromString(coordsStr);
        final Dependency d = new Dependency();
        d.setGroupId(coords.getGroupId());
        d.setArtifactId(coords.getArtifactId());
        d.setClassifier(coords.getClassifier());
        d.setType(coords.getType());
        d.setVersion(coords.getVersion());
        return managedDep(d);
    }

    @SuppressWarnings("unchecked")
    public T module(String module) {
        model.addModule(module);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T parent(String groupId, String artifactId, String version) {
        final Parent p = new Parent();
        model.setParent(p);
        p.setGroupId(groupId);
        p.setArtifactId(artifactId);
        p.setVersion(version);
        return (T) this;
    }

    public Model model() {
        return model;
    }

    @SuppressWarnings("unchecked")
    private T managedDep(Dependency d) {
        DependencyManagement dm = model.getDependencyManagement();
        if (dm == null) {
            dm = new DependencyManagement();
            model.setDependencyManagement(dm);
        }
        dm.addDependency(d);
        return (T) this;
    }
}
