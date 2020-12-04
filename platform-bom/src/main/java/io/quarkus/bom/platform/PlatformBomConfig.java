package io.quarkus.bom.platform;

import io.quarkus.bom.PomResolver;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class PlatformBomConfig {

    public static class Builder {

        private PomResolver pomResolver;
        private Map<AppArtifactKey, Artifact> enforced = new HashMap<>(0);
        private Set<AppArtifactKey> excluded = new HashSet<>(0);
        private Set<String> excludedGroups = new HashSet<>(0);
        private MavenArtifactResolver artifactResolver;

        private Builder() {
        }

        public Builder pomResolver(PomResolver pomResolver) {
            this.pomResolver = pomResolver;
            return this;
        }

        public Builder enforce(String groupId, String artifactId, String version) {
            return enforce(new DefaultArtifact(groupId, artifactId, null, "jar", version));
        }

        public Builder enforce(Artifact artifact) {
            enforced.put(new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                    artifact.getExtension()), artifact);
            return this;
        }

        public Builder excludeGroupId(String groupId) {
            excludedGroups.add(groupId);
            return this;
        }

        public Builder exclude(String groupId, String artifactId) {
            return exclude(new AppArtifactKey(groupId, artifactId, null, "jar"));
        }

        public Builder exclude(AppArtifactKey key) {
            excluded.add(key);
            return this;
        }

        public Builder artifactResolver(MavenArtifactResolver resolver) {
            this.artifactResolver = resolver;
            return this;
        }

        public PlatformBomConfig build() {
            Objects.requireNonNull(pomResolver);

            Path pom = pomResolver.pomPath();
            try {
                final Model model = pomResolver.readLocalModel(pom);
                final DependencyManagement dm = model.getDependencyManagement();
                if (dm == null) {
                    throw new Exception(pom + " does not include managed dependencies");
                }

                final Properties allProps = new Properties();
                allProps.putAll(model.getProperties());
                Parent parent = model.getParent();
                while (parent != null) {
                    final String relativePath = parent.getRelativePath();
                    if (relativePath == null || relativePath.isEmpty()) {
                        break;
                    }
                    Path parentPom = pom.getParent().resolve(relativePath).normalize().toAbsolutePath();
                    final Model parentModel = pomResolver.readLocalModel(parentPom);
                    if (parentModel == null) {
                        break;
                    }
                    allProps.putAll(parentModel.getProperties());
                    parent = parentModel.getParent();
                    pom = parentPom;
                }
                allProps.setProperty("project.version", ModelUtils.getVersion(model));

                final PlatformBomConfig config = new PlatformBomConfig();
                config.bomResolver = pomResolver;
                config.bomArtifact = Objects.requireNonNull(new DefaultArtifact(ModelUtils.getGroupId(model),
                        model.getArtifactId(), null, "pom", ModelUtils.getVersion(model)));
                for (Dependency dep : dm.getDependencies()) {
                    final Artifact artifact = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(),
                            resolveExpr(allProps, dep.getClassifier()), dep.getType(), resolveExpr(allProps, dep.getVersion()));
                    if (config.quarkusBom == null && dep.getArtifactId().equals("quarkus-bom")
                            && dep.getGroupId().equals("io.quarkus")) {
                        config.quarkusBom = artifact;
                    } else {
                        List<org.eclipse.aether.graph.Exclusion> aetherExclusions = null;
                        if (!dep.getExclusions().isEmpty()) {
                            aetherExclusions = new ArrayList<>(dep.getExclusions().size());
                            for (Exclusion e : dep.getExclusions()) {
                                aetherExclusions.add(
                                        new org.eclipse.aether.graph.Exclusion(e.getGroupId(), e.getArtifactId(), null, null));
                            }
                        }
                        org.eclipse.aether.graph.Dependency aetherDep = new org.eclipse.aether.graph.Dependency(
                                artifact, dep.getScope(),
                                dep.getOptional() != null && Boolean.parseBoolean(dep.getOptional()),
                                aetherExclusions);
                        config.directDeps.add(aetherDep);
                    }
                }
                if (config.quarkusBom == null) {
                    throw new RuntimeException("Failed to locate io.quarkus:quarkus-bom among the dependencies");
                }
                config.enforced = enforced;
                config.excluded = excluded;
                config.excludedGroups = excludedGroups;
                config.artifactResolver = artifactResolver;
                return config;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize platform BOM config", e);
            }
        }

        private static String resolveExpr(Properties allProps, String expr) {
            if (expr != null && expr.startsWith("${") && expr.endsWith("}")) {
                final String prop = expr.substring(2, expr.length() - 1);
                final String value = allProps.getProperty(prop);
                if (value != null) {
                    return value;
                }
            }
            return expr;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PlatformBomConfig forPom(PomResolver pomResolver) {
        return builder().pomResolver(pomResolver).build();
    }

    private PomResolver bomResolver;
    private Artifact bomArtifact;
    private Artifact quarkusBom;
    private List<org.eclipse.aether.graph.Dependency> directDeps = new ArrayList<>();
    private Map<AppArtifactKey, Artifact> enforced;
    private Set<AppArtifactKey> excluded;
    private Set<String> excludedGroups;
    private MavenArtifactResolver artifactResolver;

    private PlatformBomConfig() {
    }

    public PomResolver bomResolver() {
        return bomResolver;
    }

    public Artifact bomArtifact() {
        return bomArtifact;
    }

    public Artifact quarkusBom() {
        return quarkusBom;
    }

    public List<org.eclipse.aether.graph.Dependency> directDeps() {
        return directDeps;
    }

    public boolean hasEnforced() {
        return !enforced.isEmpty();
    }

    public Map<AppArtifactKey, Artifact> enforced() {
        return enforced;
    }

    public Artifact enforced(AppArtifactKey key) {
        return enforced.get(key);
    }

    public boolean hasExcluded() {
        return !excluded.isEmpty();
    }

    public Set<AppArtifactKey> excluded() {
        return excluded;
    }

    public MavenArtifactResolver artifactResolver() {
        return artifactResolver;
    }

    boolean excluded(AppArtifactKey key) {
        return excluded.contains(key) ? true : excludedGroups.contains(key.getGroupId());
    }
}
