package io.quarkus.bom.decomposer.maven;

import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * Reports non-productized dependencies of supported Quarkus extensions.
 */
@Mojo(name = "non-productized", threadSafe = true)
public class NonProductizedDepsMojo extends AbstractMojo {

    @Component
    private RepositorySystem repoSystem;

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(required = true)
    String bom;

    private final Set<ArtifactCoords> productized = new HashSet<>();
    private final Map<Integer, Set<ArtifactCoords>> nonProductized = new HashMap<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final ArtifactCoords bomCoords = ArtifactCoords.fromString(bom);
        final ArtifactCoords catalogCoords = PlatformArtifacts.getCatalogArtifactForBom(bomCoords);

        final Path jsonPath;
        try {
            jsonPath = repoSystem.resolveArtifact(repoSession, new ArtifactRequest()
                    .setRepositories(repos)
                    .setArtifact(new DefaultArtifact(catalogCoords.getGroupId(), catalogCoords.getArtifactId(),
                            catalogCoords.getClassifier(), catalogCoords.getType(), catalogCoords.getVersion())))
                    .getArtifact().getFile().toPath();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve " + catalogCoords, e);
        }

        final ExtensionCatalog catalog;
        try {
            catalog = ExtensionCatalog.fromFile(jsonPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse " + jsonPath, e);
        }

        final List<Dependency> managedDeps;
        try {
            managedDeps = repoSystem.readArtifactDescriptor(repoSession, new ArtifactDescriptorRequest()
                    .setRepositories(repos)
                    .setArtifact(new DefaultArtifact(bomCoords.getGroupId(), bomCoords.getArtifactId(),
                            "", ArtifactCoords.TYPE_POM, bomCoords.getVersion())))
                    .getManagedDependencies();
        } catch (ArtifactDescriptorException e) {
            throw new MojoExecutionException("Failed to resolve the descriptor of " + bomCoords, e);
        }
        final Set<ArtifactCoords> managedCoords = new HashSet<>(managedDeps.size());
        managedDeps.forEach(d -> managedCoords.add(toCoords(d.getArtifact())));

        final List<Extension> supported = new ArrayList<>();
        catalog.getExtensions().forEach(e -> {
            Object o = e.getMetadata().get("redhat-support");
            if (o == null) {
                return;
            }
            supported.add(e);

            final DependencyNode root;
            try {
                final Artifact a = new DefaultArtifact(e.getArtifact().getGroupId(),
                        e.getArtifact().getArtifactId(), e.getArtifact().getClassifier(),
                        e.getArtifact().getType(), e.getArtifact().getVersion());
                root = repoSystem.collectDependencies(repoSession, new CollectRequest()
                        .setManagedDependencies(managedDeps)
                        .setRepositories(repos)
                        .setRoot(new Dependency(a, "runtime")))
                        .getRoot();
            } catch (DependencyCollectionException e1) {
                throw new RuntimeException("Failed to collect dependencies of " + e.getArtifact().toCompactCoords(), e1);
            }

            if (!isProductized(e.getArtifact().getVersion())) {
                nonProductized.computeIfAbsent(0, k -> new HashSet<>()).add(e.getArtifact());
            } else {
                productized.add(e.getArtifact());
            }
            root.getChildren().forEach(n -> processNodes(n, 1));
        });

        if (nonProductized.isEmpty()) {
            return;
        }

        final Set<ArtifactCoords> allNonProductized = new HashSet<>();
        System.out.println("Non-productized artifacts of supported extensions:");
        final List<Integer> levels = new ArrayList<>(nonProductized.keySet());
        Collections.sort(levels);
        for (Integer i : levels) {
            final Set<ArtifactCoords> set = nonProductized.get(i);
            System.out.println("Level " + i + " (" + set.size() + " artifacts):");
            int managed = 0;
            int notManaged = 0;
            for (ArtifactCoords c : set) {
                System.out.println(" - " + c);
                allNonProductized.add(c);
                if (managedCoords.contains(c)) {
                    ++managed;
                } else {
                    ++notManaged;
                }
            }
            System.out.println("  managed: " + managed);
            System.out.println("  non-managed: " + notManaged);
        }

        System.out.println("Extensions: " + supported.size());
        System.out.println("Non-productized (" + allNonProductized.size() + " artifacts):");
        int managed = 0;
        int notManaged = 0;
        for (ArtifactCoords c : allNonProductized) {
            if (managedCoords.contains(c)) {
                ++managed;
            } else {
                ++notManaged;
            }
        }
        System.out.println("  managed: " + managed);
        System.out.println("  non-managed: " + notManaged);

        System.out.println("Productized (" + productized.size() + " artifacts):");
        managed = 0;
        notManaged = 0;
        for (ArtifactCoords c : productized) {
            if (managedCoords.contains(c)) {
                ++managed;
            } else {
                ++notManaged;
            }
        }
        System.out.println("  managed: " + managed);
        System.out.println("  non-managed: " + notManaged);
    }

    private static boolean isProductized(String v) {
        return v.contains("redhat");
    }

    private void processNodes(DependencyNode node, int level) {
        final ArtifactCoords coords = toCoords(node.getArtifact());
        if (!isProductized(coords.getVersion())) {
            nonProductized.computeIfAbsent(level, k -> new HashSet<>()).add(coords);
        } else {
            productized.add(coords);
        }
        for (DependencyNode child : node.getChildren()) {
            processNodes(child, level + 1);
        }
    }

    private static ArtifactCoords toCoords(Artifact a) {
        return new ArtifactCoords(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }
}
