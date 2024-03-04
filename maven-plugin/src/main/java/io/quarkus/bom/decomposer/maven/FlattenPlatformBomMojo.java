package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;

/**
 * This goal flattens the BOM, i.e. generates its effective content, and replaces the original POM
 * associated with the project with newly generated one.
 * 
 * By default, it sorts the dependency constraints alphabetically but it could be turned off.
 * The exception is Quarkus platform descriptor and property artifacts. They are moved to the top
 * of the dependency constraint list and their ordering is preserved (i.e. they are excluded from the
 * alphabetic ordering).
 */
@Mojo(name = "flatten-platform-bom", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE, threadSafe = true)
public class FlattenPlatformBomMojo extends AbstractMojo {

    @Component
    RepositorySystem repoSystem;

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(defaultValue = "${skipPlatformBom}")
    protected boolean skip;

    /**
     * Whether to order the dependency constraints alphabetically.
     */
    @Parameter(property = "alphabetically", defaultValue = "true")
    boolean alphabetically;

    /**
     * Artifacts that should be excluded from the BOM's managed dependencies
     * specified in the format groupId:artifactId[:classifier|:classifier:type]`.
     */
    @Parameter
    List<String> excludeArtifactKeys = Collections.emptyList();

    @Parameter(required = true, defaultValue = "${project.build.directory}/flattened-${project.artifactId}-${project.version}.pom")
    File outputFile;

    @Parameter(required = false, property = "filterInvalidConstraints")
    boolean filterInvalidConstraints;

    @Parameter(required = false, property = "excludeScopes")
    List<String> excludeScopes;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final Artifact artifact = project.getArtifact();
        final DefaultArtifact bomArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, artifact.getVersion());
        final ArtifactDescriptorResult bomDescriptor;
        try {
            bomDescriptor = repoSystem.readArtifactDescriptor(repoSession,
                    new ArtifactDescriptorRequest()
                            .setArtifact(bomArtifact)
                            .setRepositories(repos));
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to read artifact descriptor for " + bomArtifact, e);
        }

        final DependencyManagement dm = new DependencyManagement();

        final Set<ArtifactKey> excludedKeys = new HashSet<>(excludeArtifactKeys.size());
        if (!excludeArtifactKeys.isEmpty()) {
            for (String keyStr : excludeArtifactKeys) {
                excludedKeys.add(ArtifactKey.fromString(keyStr));
            }
        }

        final List<Dependency> managedDeps = bomDescriptor.getManagedDependencies();
        final Map<String, org.apache.maven.model.Dependency> modelDeps = alphabetically ? new HashMap<>(managedDeps.size())
                : null;
        for (Dependency d : managedDeps) {
            if (!excludeScopes.isEmpty() && excludeScopes.contains(d.getScope())) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Excluded " + d + " by scope");
                }
                continue;
            }

            final org.eclipse.aether.artifact.Artifact a = d.getArtifact();
            final String type = a.getProperties().getOrDefault("type", a.getExtension());
            final ArtifactKey key = ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(),
                    type);
            if (excludedKeys.contains(key)) {
                continue;
            }
            final org.apache.maven.model.Dependency modelDep = toModelDep(d);
            if (a.getArtifactId().endsWith(BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX) ||
                    a.getArtifactId().endsWith(BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)) {
                dm.addDependency(modelDep);
                continue;
            }

            if (filterInvalidConstraints && !exists(a)) {
                getLog().warn(a + " could not be resolved and was removed from the BOM");
                continue;
            }

            if (modelDeps != null) {
                modelDeps.put(key.toString(), modelDep);
            } else {
                dm.addDependency(modelDep);
            }

            if ("tests".equals(modelDep.getClassifier()) && "test-jar".equals(modelDep.getType())) {
                // Often in BOMs the classifier 'tests' is omitted for artifacts with type 'test-jar'
                // in which case it will be filled in by the descriptor resolver.
                // To not break the actual dependencies relying on what was exactly configured in the BOMs,
                // we also include the 'test-jar' constraint but w/o the classifier, just in case 
                org.apache.maven.model.Dependency noClassifier = modelDep.clone();
                noClassifier.setClassifier(null);
                if (modelDeps != null) {
                    modelDeps.put(ArtifactKey.of(a.getGroupId(), a.getArtifactId(), null, type).toString(), noClassifier);
                } else {
                    dm.addDependency(noClassifier);
                }
            }
        }

        if (modelDeps != null) {
            final List<String> keys = new ArrayList<>(modelDeps.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                dm.addDependency(modelDeps.get(key));
            }
        }

        final Model newModel = PomUtils.initModel(project.getModel());
        newModel.setDependencyManagement(dm);

        try {
            outputFile.getParentFile().mkdirs();
            ModelUtils.persistModel(outputFile.toPath(), newModel);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persist flattened platform bom to " + outputFile, e);
        }
        project.setPomFile(outputFile);
    }

    private boolean exists(final org.eclipse.aether.artifact.Artifact a) {
        return a.getClassifier().isEmpty()
                && resolve(new DefaultArtifact(a.getGroupId(), a.getArtifactId(), "pom", a.getVersion())) || resolve(a);
    }

    private boolean resolve(final org.eclipse.aether.artifact.Artifact a) {
        try {
            return repoSystem.resolveArtifact(repoSession,
                    new ArtifactRequest().setArtifact(a).setRepositories(repos))
                    .isResolved();
        } catch (Exception e) {
            return false;
        }
    }

    private static org.apache.maven.model.Dependency toModelDep(Dependency d) {
        final org.eclipse.aether.artifact.Artifact a = d.getArtifact();
        final org.apache.maven.model.Dependency modelDep = new org.apache.maven.model.Dependency();
        modelDep.setGroupId(a.getGroupId());
        modelDep.setArtifactId(a.getArtifactId());
        if (!a.getClassifier().isEmpty()) {
            modelDep.setClassifier(a.getClassifier());
        }

        modelDep.setType(a.getProperties().getOrDefault("type", a.getExtension()));

        modelDep.setVersion(a.getVersion());
        if (d.getScope() != null && !d.getScope().isEmpty() && !"compile".equals(d.getScope())) {
            modelDep.setScope(d.getScope());
        }
        if (d.isOptional()) {
            modelDep.setOptional(true);
        }
        if (!d.getExclusions().isEmpty()) {
            for (Exclusion e : d.getExclusions()) {
                org.apache.maven.model.Exclusion modelExcl = new org.apache.maven.model.Exclusion();
                modelExcl.setGroupId(e.getGroupId());
                modelExcl.setArtifactId(e.getArtifactId());
                modelDep.addExclusion(modelExcl);
            }
        }

        return modelDep;
    }
}
