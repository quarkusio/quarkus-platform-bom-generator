package io.quarkus.bom.platform;

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
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bom.PomResolver;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class PlatformBomConfig {

	public static class Builder {

		private PomResolver pomResolver;
		private Map<AppArtifactKey, Artifact> enforced = new HashMap<>(0);
		private Set<AppArtifactKey> excluded = new HashSet<>(0);
		private Set<String> excludedGroups = new HashSet<>(0);

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
			enforced.put(new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension()), artifact);
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

		public PlatformBomConfig build() {
			Objects.requireNonNull(pomResolver);

			Path pom = pomResolver.pomPath();
			try {
			    final Model model = pomResolver.readLocalModel(pom);
				final DependencyManagement dm = model.getDependencyManagement();
			    if(dm == null) {
                    throw new Exception(pom + " does not include managed dependencies");
			    }

			    final Properties allProps = new Properties();
			    allProps.putAll(model.getProperties());
			    Parent parent = model.getParent();
				while (parent != null) {
					final String relativePath = parent.getRelativePath();
					if(relativePath == null || relativePath.isEmpty()) {
						break;
					}
					Path parentPom = pom.getParent().resolve(relativePath).normalize().toAbsolutePath();
					final Model parentModel = pomResolver.readLocalModel(parentPom);
					if(parentModel == null) {
						break;
					}
					allProps.putAll(parentModel.getProperties());
					parent = parentModel.getParent();
					pom = parentPom;
				}
				final PlatformBomConfig config = new PlatformBomConfig();
				config.bomResolver = pomResolver;
				config.bomArtifact = Objects.requireNonNull(new DefaultArtifact(ModelUtils.getGroupId(model),
										model.getArtifactId(), null, "pom", ModelUtils.getVersion(model)));
				for(Dependency dep : dm.getDependencies()) {
					String version = dep.getVersion();
					if(version.startsWith("${") && version.endsWith("}")) {
						String prop = version.substring(2, version.length() - 1);
						String value = allProps.getProperty(prop);
						if(value != null) {
							version = value;
						}
					}
					final Artifact artifact = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getClassifier(), dep.getType(), version);
					if(config.quarkusBom == null && artifact.getArtifactId().equals("quarkus-bom") && artifact.getGroupId().equals("io.quarkus")) {
						config.quarkusBom = artifact;
					} else {
					    config.directDeps.add(artifact);
					}
				}
				if(config.quarkusBom == null) {
					throw new RuntimeException("Failed to locate io.quarkus:quarkus-bom among the dependencies");
				}
				config.enforced = enforced;
				config.excluded = excluded;
				config.excludedGroups = excludedGroups;
				return config;
			} catch (Exception e) {
				throw new RuntimeException("Failed to initialize platform BOM config", e);
			}
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
	private List<Artifact> directDeps = new ArrayList<>();
	private Map<AppArtifactKey, Artifact> enforced;
	private Set<AppArtifactKey> excluded;
	private Set<String> excludedGroups;

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

	public List<Artifact> directDeps() {
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

	boolean excluded(AppArtifactKey key) {
		return excluded.contains(key) ? true : excludedGroups.contains(key.getGroupId());
	}
}
