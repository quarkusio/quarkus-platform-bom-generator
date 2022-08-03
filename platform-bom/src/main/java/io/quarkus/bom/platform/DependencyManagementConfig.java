package io.quarkus.bom.platform;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

public class DependencyManagementConfig {

    private List<DependencySpec> dependencies = new ArrayList<>();

    public void applyOverrides(DependencyManagementConfig overrides) {
        if (overrides.dependencies.isEmpty()) {
            return;
        }
        if (dependencies.isEmpty()) {
            dependencies = overrides.dependencies;
            return;
        }

        final Map<String, DependencySpec> overrideSpecs = new LinkedHashMap<>(overrides.dependencies.size());
        overrides.dependencies.forEach(d -> overrideSpecs.put(d.getArtifact(), d));
        for (int i = 0; i < dependencies.size(); ++i) {
            final DependencySpec override = overrideSpecs.remove(dependencies.get(i).getArtifact());
            if (override != null) {
                dependencies.set(i, override);
            }
        }
        for (DependencySpec d : overrideSpecs.values()) {
            dependencies.add(d);
        }
    }

    public void addDependency(String dependency) {
        final DependencySpec dep = new DependencySpec();
        dep.setArtifact(dependency);
        dependencies.add(dep);
    }

    public void addDependencySpec(DependencySpec spec) {
        dependencies.add(spec);
    }

    public List<DependencySpec> getDependencies() {
        return dependencies;
    }

    public List<Dependency> toAetherDependencies() {
        if (dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Dependency> result = new ArrayList<>(dependencies.size());
        for (DependencySpec dep : dependencies) {
            final ArtifactCoords coords = ArtifactCoords.fromString(dep.getArtifact());
            final List<Exclusion> exclusions = new ArrayList<>(dep.getExclusions().size());
            for (String e : dep.getExclusions()) {
                final ArtifactKey key = ArtifactKey.fromString(e);
                exclusions.add(new Exclusion(key.getGroupId(), key.getArtifactId(), key.getClassifier(), key.getType()));
            }
            result.add(new Dependency(new DefaultArtifact(coords.getGroupId(),
                    coords.getArtifactId(), coords.getClassifier(), coords.getType(), coords.getVersion()), "compile", null,
                    exclusions));
        }
        return result;
    }
}
