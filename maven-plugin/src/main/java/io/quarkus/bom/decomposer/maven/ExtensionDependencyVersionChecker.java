package io.quarkus.bom.decomposer.maven;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.util.GlobUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;

public class ExtensionDependencyVersionChecker {

    private static final String REDHAT_SUPPORT = "redhat-support";
    private static final Set<String> CHECK_SUPPORT_LEVELS = Set.of("supported");

    public static Builder builder() {
        return new ExtensionDependencyVersionChecker().new Builder();
    }

    public class Builder {

        private Builder() {
        }

        public Builder setRepositorySystem(RepositorySystem system) {
            repoSystem = system;
            return this;
        }

        public Builder setRepositorySystemSession(RepositorySystemSession session) {
            repoSession = session;
            return this;
        }

        public Builder setRemoteRepositories(List<RemoteRepository> remoteRepos) {
            repos = remoteRepos;
            return this;
        }

        public Builder setVersionPattern(String versionPattern) {
            globVersionPattern = versionPattern;
            return this;
        }

        public Builder setDepth(int depth) {
            checkDepth = depth;
            return this;
        }

        public ExtensionDependencyVersionChecker build() {
            Objects.requireNonNull(repoSystem, "Repository system wasn't initialized");
            Objects.requireNonNull(repoSession, "Repository system session wasn't initialized");
            Objects.requireNonNull(repos, "Remote repositories weren't initialized");
            Objects.requireNonNull(globVersionPattern, "Version pattern wasn't initialized");
            ExtensionDependencyVersionChecker.this.versionPattern = Pattern
                    .compile(GlobUtil.toRegexPattern(globVersionPattern));
            if (ExtensionDependencyVersionChecker.this.checkSupportLevels == null) {
                ExtensionDependencyVersionChecker.this.checkSupportLevels = CHECK_SUPPORT_LEVELS;
            }
            return ExtensionDependencyVersionChecker.this;
        }
    }

    private RepositorySystem repoSystem;
    private RepositorySystemSession repoSession;
    private List<RemoteRepository> repos;
    private String globVersionPattern;
    private Pattern versionPattern;
    private int checkDepth = Integer.MAX_VALUE;
    private Set<String> checkSupportLevels;

    public List<String> checkDependencyVersions(ExtensionCatalog catalog) {

        var bom = catalog.getBom();
        final List<Dependency> bomConstraints;
        try {
            bomConstraints = repoSystem.readArtifactDescriptor(repoSession, new ArtifactDescriptorRequest()
                    .setArtifact(new DefaultArtifact(bom.getGroupId(), bom.getArtifactId(), bom.getClassifier(), bom.getType(),
                            bom.getVersion()))
                    .setRepositories(repos)).getManagedDependencies();
        } catch (ArtifactDescriptorException e1) {
            throw new RuntimeException("Failed to resolve " + bom, e1);
        }
        if (bomConstraints.isEmpty()) {
            throw new RuntimeException("Failed to resolve " + bom);
        }
        final Set<ArtifactKey> bomConstraintKeys = new HashSet<>(bomConstraints.size());
        bomConstraints.forEach(d -> bomConstraintKeys.add(toKey(d.getArtifact())));

        final List<String> errors = new ArrayList<>();
        catalog.getExtensions().forEach(e -> {
            Object o = e.getMetadata().get(REDHAT_SUPPORT);
            if (o == null) {
                return;
            }
            var extensionCoords = e.getArtifact();
            if (!(o instanceof List)) {
                System.out.println("Expected " + REDHAT_SUPPORT + " metadata of " + extensionCoords.toCompactCoords()
                        + " is not an instance of java.util.List but " + o.getClass().getName());
                return;
            }
            if (!((List<String>) o).stream().filter(i -> CHECK_SUPPORT_LEVELS.contains(i)).findFirst().isPresent()) {
                return;
            }
            var a = new DefaultArtifact(extensionCoords.getGroupId(), extensionCoords.getArtifactId(),
                    extensionCoords.getClassifier(), extensionCoords.getType(), extensionCoords.getVersion());
            final DependencyNode root;
            try {
                root = repoSystem.collectDependencies(repoSession, new CollectRequest()
                        .setRootArtifact(new DefaultArtifact("org.acme", "acme-app", null, "jar", "1.0-SNAPSHOT"))
                        .setManagedDependencies(bomConstraints)
                        .addDependency(new Dependency(a, "compile"))
                        .setRepositories(repos)).getRoot();
            } catch (DependencyCollectionException e1) {
                throw new IllegalStateException("Failed to collect dependencies of " + extensionCoords.toCompactCoords(), e1);
            }

            root.getChildren().forEach(
                    n -> checkExtensionDependencyVersion(n.getArtifact(), n, checkDepth, bomConstraintKeys, errors));
        });
        return errors;
    }

    private void checkExtensionDependencyVersion(Artifact root, DependencyNode node, int depth,
            Set<ArtifactKey> bomConstraintKeys, List<String> errors) {
        Artifact a = node.getArtifact();
        if (a != null && !versionPattern.matcher(a.getVersion()).matches() && bomConstraintKeys.contains(toKey(a))) {
            errors.add(toCompactCoords(root) + " depends on " + toCompactCoords(a) + " that does not match version pattern "
                    + globVersionPattern);
            return;
        }
        if (depth == 0) {
            return;
        }
        for (DependencyNode child : node.getChildren()) {
            checkExtensionDependencyVersion(root, child, depth - 1, bomConstraintKeys, errors);
        }
    }

    private static ArtifactKey toKey(Artifact a) {
        return ArtifactKey.gact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension());
    }

    private static String toCompactCoords(Artifact a) {
        final StringBuilder sb = new StringBuilder();
        sb.append(a.getGroupId()).append(':').append(a.getArtifactId()).append(':');
        if (!a.getClassifier().isEmpty()) {
            sb.append(a.getClassifier()).append(':');
        }
        if (!a.getExtension().equals(ArtifactCoords.TYPE_JAR)) {
            sb.append(a.getExtension()).append(':');
        }
        sb.append(a.getVersion());
        return sb.toString();
    }
}
