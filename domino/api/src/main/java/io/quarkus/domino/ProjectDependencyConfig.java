package io.quarkus.domino;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ProjectDependencyConfig {

    /**
     * Project directory
     * 
     * @return project directory
     */
    Path getProjectDir();

    /**
     * Project BOM
     * 
     * @return project BOM
     */
    ArtifactCoords getProjectBom();

    Collection<ArtifactCoords> getProjectArtifacts();

    Collection<ArtifactCoords> getIncludeArtifacts();

    Collection<ArtifactCoords> getIncludePatterns();

    Collection<ArtifactCoords> getExcludePatterns();

    /**
     * Dependency scopes that should be excluded resolving dependencies of root artifact.
     * If not configured, provided and test scoped dependencies will be excluded by default.
     * 
     * @return dependency scopes that should be excluded resolving dependencies of root artifact
     */
    Set<String> getExcludeScopes();

    /**
     * Whether to exclude dependencies (and their transitive dependencies) that aren't managed in the BOM. The default is true.
     * 
     * @return whether non-managed dependencies should be included
     */
    boolean isIncludeNonManaged();

    /**
     * Whether to exclude parent POMs from the list of artifacts to be built from source
     * 
     * @return whether to exclude parent POMs
     */
    boolean isExcludeParentPoms();

    /**
     * Whether to exclude BOMs imported in the POMs of artifacts to be built from the list of artifacts to be built from source
     * 
     * @return whether to exclude BOM imports
     */
    boolean isExcludeBomImports();

    /**
     * The depth level of a dependency tree of each supported Quarkus extension to capture.
     * Setting the level to 0 will target the supported extension artifacts themselves.
     * Setting the level to 1, will target the supported extension artifacts plus their direct dependencies.
     * If the level is not specified, the default will be -1, which means all the levels.
     * 
     * @return dependency level
     */
    int getLevel();

    /**
     * Whether to log the coordinates of the artifacts captured down to the depth specified. The default is true.
     * 
     * @return whether to log complete artifacts coordinates
     */
    boolean isLogArtifactsToBuild();

    /**
     * Whether to log the module GAVs the artifacts to be built belongs to instead of all
     * the complete artifact coordinates to be built.
     * If this option is enabled, it overrides {@link #isLogArtifactsToBuild()}
     * 
     * @return whether to log module coords as GAVs instead of complete artifact coordinates
     */
    boolean isLogModulesToBuild();

    /**
     * Whether to log the dependency trees walked down to the depth specified. The default is false.
     * 
     * @return whether to log dependency trees
     */
    boolean isLogTrees();

    /**
     * Comma-separated list of artifacts to log dependency trees for.
     * 
     * @return comma-separated list of artifacts to log dependency trees for
     */
    String getLogTreesFor();

    /**
     * Whether to log the coordinates of the artifacts below the depth specified. The default is false.
     * 
     * @return whether to log remaining artifacts
     */
    boolean isLogRemaining();

    /**
     * Whether to log the summary at the end. The default is true.
     * 
     * @return whether to log summary
     */
    boolean isLogSummary();

    /**
     * Whether to log visited non-managed dependencies.
     * 
     * @return whether to log visited non-managed dependencies
     */
    boolean isLogNonManagedVisitied();

    /**
     * Whether to log code repository info for the artifacts to be built from source
     * 
     * @return whether to log code repositories
     */
    boolean isLogCodeRepos();

    /**
     * Whether to log code repository dependency tree.
     * 
     * @return whetehr to log code repository dependency tree
     */
    boolean isLogCodeRepoTree();

    /**
     * A list of build recipe repository URLs
     * 
     * @return list of build recipe repository URLs
     */
    List<String> getRecipeRepos();

    /**
     * Whether to validate the discovered code repo and tags that are included in the report
     * 
     * @return whether to validate core repos and tags
     */
    boolean isValidateCodeRepoTags();

    /**
     * @deprecated Deprecated in favor of the HACBS SCM locator that performs validation
     * 
     *             Whether to use the legacy SCM detector.
     * 
     * @return whether to use the legacy SCM detector
     */
    @Deprecated(since = "0.0.78")
    boolean isLegacyScmLocator();

    /**
     * Whether to warn about errors not being able to resolve top level artifacts or fail the process
     * 
     * @return whether to warn on artifact resolution errors
     */
    boolean isWarnOnResolutionErrors();

    /**
     * Whether to issue a warning in case the SCM location could not be determined or fail with
     * an error (the default behavior).
     * 
     * @return whether to fail in case the SCM location could not be determined
     */
    boolean isWarnOnMissingScm();

    boolean isIncludeAlreadyBuilt();

    /**
     * Whether to include optional dependencies of the root project artifacts
     * 
     * @return whether to include optional dependencies of the root project artifacts
     */
    boolean isIncludeOptionalDeps();

    /**
     * Whether to use Java 8 to fetch dependency information from a Gradle project.
     * In case this method returns true, the value of JAVA8_HOME environment variable will be used as the Java 8 home directory.
     * 
     * @return whether to use Java 8 to fetch dependency information from a Gradle project
     */
    boolean isGradleJava8();

    /**
     * Java home directory that should be used when fetching dependency information from a Gradle project.
     * 
     * @return Java home directory that should be used when fetching dependency information from a Gradle project.
     */
    String getGradleJavaHome();

    default Mutable mutable() {
        return new ProjectDependencyConfigImpl.Builder(this);
    }

    /**
     * Persist this configuration to the specified file.
     *
     * @param p Target path
     * @throws IOException if the specified file can not be written to.
     */
    default void persist(Path p) throws IOException {
        ProjectDependencyConfigMapper.serialize(this, p);
    }

    interface Mutable extends ProjectDependencyConfig {

        Mutable setProjectDir(Path projectDir);

        Mutable setProjectBom(ArtifactCoords bom);

        Mutable setProjectArtifacts(Collection<ArtifactCoords> projectArtifacts);

        Mutable setIncludeArtifacts(Collection<ArtifactCoords> artifacts);

        default Mutable setIncludeGroupIds(Collection<String> groupIds) {
            groupIds.forEach(g -> addIncludePattern(
                    ArtifactCoords.of(g, ProjectDependencyConfigImpl.WILDCARD, ProjectDependencyConfigImpl.WILDCARD,
                            ProjectDependencyConfigImpl.WILDCARD, ProjectDependencyConfigImpl.WILDCARD)));
            return this;
        }

        default Mutable setIncludeKeys(Collection<ArtifactKey> artifactKeys) {
            artifactKeys.forEach(k -> addIncludePattern(ArtifactCoords.of(k.getGroupId(), k.getArtifactId(), k.getClassifier(),
                    k.getType(), ProjectDependencyConfigImpl.WILDCARD)));
            return this;
        }

        Mutable setIncludePatterns(Collection<ArtifactCoords> artifacts);

        Mutable addIncludePattern(ArtifactCoords patter);

        default Mutable setExcludeGroupIds(Collection<String> groupIds) {
            groupIds.forEach(g -> addExcludePattern(
                    ArtifactCoords.of(g, ProjectDependencyConfigImpl.WILDCARD, ProjectDependencyConfigImpl.WILDCARD,
                            ProjectDependencyConfigImpl.WILDCARD, ProjectDependencyConfigImpl.WILDCARD)));
            return this;
        }

        default Mutable setExcludeKeys(Collection<ArtifactKey> artifactKeys) {
            artifactKeys.forEach(k -> addExcludePattern(ArtifactCoords.of(k.getGroupId(), k.getArtifactId(), k.getClassifier(),
                    k.getType(), ProjectDependencyConfigImpl.WILDCARD)));
            return this;
        }

        Mutable setExcludePatterns(Collection<ArtifactCoords> artifacts);

        Mutable addExcludePattern(ArtifactCoords pattern);

        Mutable setExcludeScopes(Set<String> excludeScopes);

        Mutable setIncludeNonManaged(boolean includeNonManaged);

        Mutable setExcludeParentPoms(boolean excludeParentPoms);

        Mutable setExcludeBomImports(boolean excludeBomImports);

        Mutable setLevel(int level);

        Mutable setLogArtifactsToBuild(boolean logArtifactsToBuild);

        Mutable setLogModulesToBuild(boolean logModulesToBuild);

        Mutable setLogTrees(boolean logTrees);

        Mutable setLogTreesFor(String logTreesFor);

        Mutable setLogRemaining(boolean logRemaining);

        Mutable setLogSummary(boolean logSummary);

        Mutable setLogNonManagedVisited(boolean logNonManagedVisited);

        Mutable setLogCodeRepos(boolean logCodeRepos);

        Mutable setLogCodeRepoTree(boolean logCodeRepoGraph);

        Mutable setRecipeRepos(List<String> recipeRepoUrls);

        @Deprecated(since = "0.0.78")
        Mutable setValidateCodeRepoTags(boolean validateTags);

        Mutable setLegacyScmLocator(boolean legacyScmLocator);

        Mutable setWarnOnResolutionErrors(boolean warn);

        Mutable setWarnOnMissingScm(boolean warnOnMissingScm);

        Mutable setIncludeAlreadyBuilt(boolean includeAlreadyBuilt);

        Mutable setIncludeOptionalDeps(boolean includeOptionalDeps);

        Mutable setGradleJava8(boolean java8);

        Mutable setGradleJavaHome(String javaHome);

        ProjectDependencyConfig build();

        default void persist(Path p) throws IOException {
            ProjectDependencyConfigMapper.serialize(build(), p);
        }
    }

    /**
     * @return a new mutable instance
     */
    static Mutable builder() {
        return new ProjectDependencyConfigImpl.Builder();
    }

    /**
     * Read config from the specified file
     *
     * @param path File to read from (yaml or json)
     * @return read-only {@link ProjectDependencyConfig} object
     * @throws IOException in case of a failure
     */
    static ProjectDependencyConfig fromFile(Path path) throws IOException {
        return mutableFromFile(path).build();
    }

    /**
     * Read config from the specified file
     *
     * @param path File to read from (yaml or json)
     * @return mutable {@link ProjectDependencyConfig}
     * @throws IOException in case of a failure
     */
    static ProjectDependencyConfig.Mutable mutableFromFile(Path path) throws IOException {
        final ProjectDependencyConfig.Mutable mutable = ProjectDependencyConfigMapper.deserialize(path,
                ProjectDependencyConfigImpl.Builder.class);
        return mutable == null ? ProjectDependencyConfig.builder() : mutable;
    }

    /**
     * Read config from an input stream
     *
     * @param inputStream input stream to read from
     * @return read-only {@link ProjectDependencyConfig} object (empty/default for an empty file)
     * @throws IOException in case of a failure
     */
    static ProjectDependencyConfig fromStream(InputStream inputStream) throws IOException {
        final ProjectDependencyConfig.Mutable mutable = ProjectDependencyConfigMapper.deserialize(inputStream,
                ProjectDependencyConfigImpl.Builder.class);
        return mutable == null ? ProjectDependencyConfig.builder().build() : mutable.build();
    }
}
