package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.registry.Constants;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.ExtensionOrigin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;

public class PlatformBomUtils {

    /**
     * Persists decomposed platform BOM to a pom.xml filling in developer, SCM and other info from the base model
     * 
     * @param decomposed decomposed platform BOM
     * @param file target file
     * @param baseModel base model info
     * @throws IOException in case of a failure
     */
    public static void toPom(DecomposedBom decomposed, Path file, Model baseModel, PlatformCatalogResolver resolver)
            throws IOException {
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
        ModelUtils.persistModel(file, toPlatformModel(decomposed, baseModel, resolver));
    }

    /**
     * Converts decomposed BOM to a platform POM Model copying developer info, SCM, etc
     * from the base Model
     * 
     * @param decomposed decomposed BOM
     * @param baseModel base Model info
     * @return POM Model
     */
    public static Model toPlatformModel(DecomposedBom decomposed, Model baseModel, PlatformCatalogResolver resolver) {

        final Artifact bomArtifact = decomposed.bomArtifact();

        final Map<String, PlatformInfo> platforms = new HashMap<>();

        PlatformInfo currentPlatformInfo = null;
        final Map<String, Dependency> artifacts = new HashMap<>();
        for (ProjectRelease release : decomposed.releases()) {
            for (ProjectDependency dep : release.dependencies()) {
                final Artifact depArtifact = dep.artifact();
                if (dep.key().getArtifactId().endsWith(Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX)) {
                    final String id = depArtifact.getGroupId() + ":" + depArtifact.getArtifactId() + ":"
                            + depArtifact.getClassifier() + ":" + depArtifact.getExtension() + ":"
                            + depArtifact.getVersion();
                    final boolean currentPlatform = bomArtifact.getGroupId().equals(depArtifact.getGroupId()) &&
                            bomArtifact.getArtifactId()
                                    .equals(depArtifact.getArtifactId().substring(0, depArtifact.getArtifactId().length()
                                            - Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX.length()));
                    final PlatformInfo platform = platforms.computeIfAbsent(id, k -> new PlatformInfo(id, currentPlatform));
                    if (platform.descriptor != null) {
                        throw new IllegalStateException(
                                "Platform version conflict: " + platform.descriptor + " vs " + depArtifact);
                    }
                    platform.descriptor = dep;
                    if (currentPlatform) {
                        currentPlatformInfo = platform;
                    } else {
                        try {
                            platform.catalog = resolver.resolve(depArtifact);
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to resolve platform descriptor " + depArtifact, e);
                        }
                    }
                } else if (dep.key().getArtifactId().endsWith(Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)) {
                    final String id = depArtifact.getGroupId() + ":" + depArtifact.getArtifactId().substring(0,
                            depArtifact.getArtifactId().length()
                                    - Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX.length())
                            + Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX + ":" + depArtifact.getVersion() + ":json:"
                            + depArtifact.getVersion();
                    final boolean currentPlatform = bomArtifact.getGroupId().equals(depArtifact.getGroupId()) &&
                            bomArtifact.getArtifactId()
                                    .equals(depArtifact.getArtifactId().substring(0, depArtifact.getArtifactId().length()
                                            - Constants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX.length()));
                    final PlatformInfo platform = platforms.computeIfAbsent(id, k -> new PlatformInfo(id, currentPlatform));
                    if (currentPlatform) {
                        currentPlatformInfo = platform;
                    }
                    if (platform.properties != null) {
                        throw new IllegalStateException(
                                "Platform version conflict: " + platform.properties + " vs " + depArtifact);
                    }
                    platform.properties = dep;
                } else {
                    artifacts.put(dep.key().toString(), PomUtils.toModelDep(dep));
                }
            }
        }

        final DependencyManagement dm = new DependencyManagement();

        if (currentPlatformInfo != null) {
            add(dm, currentPlatformInfo);
        }
        for (PlatformInfo root : getTopPlatforms(platforms, decomposed.bomArtifact())) {
            add(dm, root);
        }

        final List<String> keys = new ArrayList<>(artifacts.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            dm.addDependency(artifacts.get(key));
        }

        final Model model = PomUtils.initModel(baseModel);
        model.setGroupId(decomposed.bomArtifact().getGroupId());
        model.setArtifactId(decomposed.bomArtifact().getArtifactId());
        model.setVersion(decomposed.bomArtifact().getVersion());
        model.setDependencyManagement(dm);
        return model;
    }

    private static void add(DependencyManagement dm, PlatformInfo platform) {
        if (platform.addedToBom) {
            return;
        }
        platform.addedToBom = true;
        dm.addDependency(PomUtils.toModelDep(platform.descriptor));
        if (platform.properties != null) {
            dm.addDependency(PomUtils.toModelDep(platform.properties));
        }
        if (platform.imports.isEmpty()) {
            for (PlatformInfo imported : platform.imports) {
                add(dm, imported);
            }
        }
    }

    private static List<PlatformInfo> getTopPlatforms(Map<String, PlatformInfo> platforms, Artifact currentBom) {
        final Set<String> allDerivedFrom = new HashSet<>(platforms.size());
        for (PlatformInfo platform : platforms.values()) {
            if (platform.currentPlatform) {
                continue;
            }
            if (platform.catalog == null) {
                throw new IllegalStateException("Failed to locate platform descriptor artifact " + platform.id
                        + " among the managed dependencies of " + currentBom);
            }
            for (ExtensionOrigin o : platform.catalog.getDerivedFrom()) {
                allDerivedFrom.add(o.getId());
                platform.imports.add(platforms.get(o.getId()));
            }
        }
        final List<PlatformInfo> roots = new ArrayList<>(platforms.size());
        for (PlatformInfo platform : platforms.values()) {
            if (!platform.currentPlatform && !allDerivedFrom.contains(platform.catalog.getId())) {
                roots.add(platform);
            }
        }
        return roots;
    }

    private static class PlatformInfo {
        final String id;
        final boolean currentPlatform;
        ExtensionCatalog catalog;
        ProjectDependency descriptor;
        ProjectDependency properties;
        final List<PlatformInfo> imports = new ArrayList<>(2);
        boolean addedToBom;

        PlatformInfo(String id, boolean currentPlatform) {
            this.id = id;
            this.currentPlatform = currentPlatform;
        }
    }
}
