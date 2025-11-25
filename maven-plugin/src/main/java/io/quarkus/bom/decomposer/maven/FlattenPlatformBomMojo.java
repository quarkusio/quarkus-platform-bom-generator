package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.domino.ArtifactSet;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactCoordsPattern;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
     * GLOB patters are allowed.
     */
    @Parameter
    List<String> excludeArtifactKeys = List.of();
    List<ArtifactCoordsPattern> excludePatterns;

    @Parameter(required = true, defaultValue = "${project.build.directory}/flattened-${project.artifactId}-${project.version}.pom")
    File outputFile;

    /**
     * @deprecated use {@link #invalidConstraintsRemedy} instead: {@code dont_validate} for
     *             {@code filterInvalidConstraints=false} and {@code exclude} for {@code filterInvalidConstraints=true}
     */
    @Deprecated
    @Parameter(required = false, property = "filterInvalidConstraints")
    Boolean filterInvalidConstraints;

    /**
     * How invalid BOM entries should be handled. Possible values:
     * <ul>
     * <li>{@code dont_validate} - do not check the validity of the BOM entries at all, just pass them though to the resulting
     * BOM
     * <li>{@code exclude} - check the validity of the BOM entries, warn on the console if they are invalid and do not pass
     * them though to the resulting BOM
     * <li>{@code fail} - check the validity of the BOM entries, throw an exception on the first invalid entry
     * Default is {@code dont_validate}
     * <p>
     * This is a replacement for {@link #filterInvalidConstraints}. The behavior of {@code dont_validate} and {@code exclude}
     * values maps to {@code false} and {@code true} values of {@link #filterInvalidConstraints} respectively.
     *
     * @since 0.0.127
     */
    @Parameter(property = "invalidConstraintsRemedy")
    FailureRemedy invalidConstraintsRemedy;

    public enum FailureRemedy {
        fail,
        exclude,
        dont_validate
    }

    @Parameter(required = false, property = "excludeScopes")
    List<String> excludeScopes;

    /**
     * Additional remote repositories to use for validating the entries of this BOM.
     * Each additional repository is used only for the validation of the artifacts selected by its {@code <includes>}
     * and {@code <excludes>}.
     * If some repository matches some artifact, then it is prepended to the list of remote repositories present in the
     * current Maven session.
     * The pattern format of {@code <includes>} and {@code <excludes>} is described in
     * {@link io.quarkus.domino.ArtifactCoordsPattern#of(String)}.
     * <p>
     * Example:
     * 
     * <pre>
     * {@code
     * <additionalRepos>
     *   <additionalRepo>
     *     <includes>org.foo:*,org.bar:baz:1.2.3</includes>
     *     <excludes>org.foo:foo-api</excludes>
     *     <id>maven.foo.org</id>
     *     <url>https://maven.foo.org/repo</url>
     *   </additionalRepo>
     * </additionalRepos>
     * }
     * </pre>
     *
     * @since 0.0.127
     */
    @Parameter
    List<AdditionalRepoSpec> additionalRepos;
    private List<AdditionalRepo> parsedAdditionalRepos;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (invalidConstraintsRemedy != null && filterInvalidConstraints != null) {
            /* Fail if there is an inconsistency */
            switch (invalidConstraintsRemedy) {
                case dont_validate:
                    if (filterInvalidConstraints.booleanValue() != false) {
                        throw new IllegalStateException("Inconsistent parameter values invalidConstraintsRemedy = "
                                + invalidConstraintsRemedy + " and filterInvalidConstraints = " + filterInvalidConstraints
                                + "; use only invalidConstraintsRemedy");
                    }
                    break;
                case exclude:
                    if (filterInvalidConstraints.booleanValue() != true) {
                        throw new IllegalStateException("Inconsistent parameter values invalidConstraintsRemedy = "
                                + invalidConstraintsRemedy + " and filterInvalidConstraints = " + filterInvalidConstraints
                                + "; use only invalidConstraintsRemedy");
                    }
                    break;
                case fail:
                    throw new IllegalStateException("Inconsistent parameter values invalidConstraintsRemedy = "
                            + invalidConstraintsRemedy + " and filterInvalidConstraints = " + filterInvalidConstraints
                            + "; use only invalidConstraintsRemedy");
                default:
                    throw new IllegalStateException("Unexpected invalidConstraintsRemedy value " + invalidConstraintsRemedy);
            }
        }
        if (invalidConstraintsRemedy == null) {
            invalidConstraintsRemedy = filterInvalidConstraints != null
                    ? (filterInvalidConstraints.booleanValue() ? FailureRemedy.exclude : FailureRemedy.dont_validate)
                    : FailureRemedy.dont_validate;
        }

        parsedAdditionalRepos = additionalRepos();

        final ArtifactDescriptorResult bomDescriptor = resolveBomDescriptor();
        initExcludePatterns();

        final DependencyManagement dm = new DependencyManagement();

        final List<Dependency> managedDeps = bomDescriptor.getManagedDependencies();
        final Map<String, org.apache.maven.model.Dependency> modelDeps = alphabetically ? new HashMap<>(managedDeps.size())
                : null;
        StringBuilder invalidConstraintsErrorMessage = null;
        for (Dependency d : managedDeps) {
            if (!excludeScopes.isEmpty() && excludeScopes.contains(d.getScope())) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Excluded " + d + " by scope");
                }
                continue;
            }

            if (isExcluded(d.getArtifact())) {
                continue;
            }
            final org.eclipse.aether.artifact.Artifact a = d.getArtifact();
            final String type = a.getProperties().getOrDefault("type", a.getExtension());
            final org.apache.maven.model.Dependency modelDep = toModelDep(d);
            if (a.getArtifactId().endsWith(BootstrapConstants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX) ||
                    a.getArtifactId().endsWith(BootstrapConstants.PLATFORM_PROPERTIES_ARTIFACT_ID_SUFFIX)) {
                dm.addDependency(modelDep);
                continue;
            }

            if (invalidConstraintsRemedy != FailureRemedy.dont_validate && !exists(a)) {
                if (invalidConstraintsRemedy == FailureRemedy.exclude) {
                    getLog().warn(a + " could not be resolved and was removed from the BOM");
                    continue;
                } else {
                    /* Should be invalidConstraintsRemedy == FailureRemedy.fail */
                    if (invalidConstraintsErrorMessage == null) {
                        invalidConstraintsErrorMessage = new StringBuilder("Unresolvable dependencyManagement entries in ")
                                .append(project.getArtifactId())
                                .append("; you may want to remove them from the source pom.xml file or exclude it via excludeArtifactKeys or excludePatterns:");
                    }
                    invalidConstraintsErrorMessage.append("\n    - ").append(a);
                }
            }

            if (modelDeps != null) {
                final ArtifactKey key = ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(),
                        type);
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
        if (invalidConstraintsErrorMessage != null) {
            throw new MojoFailureException(invalidConstraintsErrorMessage.toString());
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

    private List<AdditionalRepo> additionalRepos() {
        if (additionalRepos == null || additionalRepos.isEmpty()) {
            return Collections.emptyList();
        }
        return additionalRepos.stream()
                .map(AdditionalRepoSpec::create)
                .collect(Collectors.toUnmodifiableList());
    }

    private boolean isExcluded(org.eclipse.aether.artifact.Artifact a) {
        if (excludePatterns.isEmpty()) {
            return false;
        }
        final String type = a.getProperties().getOrDefault("type", a.getExtension());
        for (var pattern : excludePatterns) {
            if (pattern.matches(a.getGroupId(), a.getArtifactId(), a.getClassifier(), type, a.getVersion())) {
                return true;
            }
        }
        return false;
    }

    private void initExcludePatterns() {
        final List<ArtifactCoordsPattern> patterns = new ArrayList<>(excludeArtifactKeys.size());
        if (!excludeArtifactKeys.isEmpty()) {
            for (String keyStr : excludeArtifactKeys) {
                patterns.add(ArtifactCoordsPattern.of(keyStr));
            }
        }
        this.excludePatterns = patterns;
    }

    private ArtifactDescriptorResult resolveBomDescriptor() throws MojoExecutionException {
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
        return bomDescriptor;
    }

    private boolean exists(final org.eclipse.aether.artifact.Artifact a) {
        return a.getClassifier().isEmpty()
                && resolve(new DefaultArtifact(a.getGroupId(), a.getArtifactId(), "pom", a.getVersion())) || resolve(a);
    }

    private boolean resolve(final org.eclipse.aether.artifact.Artifact a) {
        try {
            final List<RemoteRepository> useRepos;
            final Optional<AdditionalRepo> additionalRepo = parsedAdditionalRepos.stream()
                    .filter(repo -> repo.contains(a))
                    .findFirst();
            if (additionalRepo.isPresent()) {
                useRepos = new ArrayList<>(repos.size() + 1);
                useRepos.add(additionalRepo.get().remoteRepository);
                useRepos.addAll(repos);
                getLog().warn("repos for " + a + ": " + useRepos);
            } else {
                useRepos = repos;
            }
            return repoSystem.resolveArtifact(repoSession,
                    new ArtifactRequest().setArtifact(a).setRepositories(useRepos))
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

    static class AdditionalRepo {
        private final ArtifactSet artifactSet;
        private final org.eclipse.aether.repository.RemoteRepository remoteRepository;

        public AdditionalRepo(ArtifactSet artifactSet, RemoteRepository remoteRepository) {
            super();
            this.artifactSet = artifactSet;
            this.remoteRepository = remoteRepository;
        }

        public boolean contains(org.eclipse.aether.artifact.Artifact a) {
            return artifactSet.contains(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), a.getVersion());
        }
    }

    public static class AdditionalRepoSpec {
        final ArtifactSet.Builder artifactSet = ArtifactSet.builder();
        String id;
        String url;

        public AdditionalRepo create() {
            return new AdditionalRepo(
                    artifactSet.build(),
                    new RemoteRepository.Builder(id, "default", url).build());
        }

        public void setIncludes(String includes) {
            artifactSet.includes(includes);
        }

        public void setExcludes(String excludes) {
            artifactSet.excludes(excludes);
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
