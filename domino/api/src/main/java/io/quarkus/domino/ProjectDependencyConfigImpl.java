package io.quarkus.domino;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ProjectDependencyConfigImpl implements ProjectDependencyConfig {

    static final String WILDCARD = "*";

    private final Path projectDir;
    private final ArtifactCoords projectBom;
    private final Collection<ArtifactCoords> projectArtifacts;
    private final Collection<ArtifactCoords> includeArtifacts;
    private final Collection<ArtifactCoords> includePatterns;
    private final Collection<ArtifactCoords> excludePatterns;
    private final boolean includeNonManaged;
    private final boolean excludeParentPoms;
    private final boolean excludeBomImports;
    private final int level;
    private final boolean logArtifactsToBuild;
    private final boolean logModulesToBuild;
    private final boolean logTrees;
    private final boolean logRemaining;
    private final boolean logSummary;
    private final boolean logNonManagedVisited;
    private final boolean logCodeRepos;
    private final boolean logCodeRepoTree;
    private final boolean validateCodeRepoTags;
    private final boolean warnOnResolutionErrors;
    private final boolean includeAlreadyBuilt;
    private final boolean includeOptionalDeps;
    private final boolean gradleJava8;
    private final String gradleJavaHome;

    private ProjectDependencyConfigImpl(ProjectDependencyConfig other) {
        this.projectDir = other.getProjectDir();
        projectBom = other.getProjectBom();
        projectArtifacts = toUnmodifiableList(other.getProjectArtifacts());
        includeArtifacts = toUnmodifiableList(other.getIncludeArtifacts());
        includePatterns = toUnmodifiableList(other.getIncludePatterns());
        excludePatterns = toUnmodifiableList(other.getExcludePatterns());
        includeNonManaged = other.isIncludeNonManaged();
        excludeParentPoms = other.isExcludeParentPoms();
        excludeBomImports = other.isExcludeBomImports();
        level = other.getLevel();
        logArtifactsToBuild = other.isLogArtifactsToBuild();
        logModulesToBuild = other.isLogModulesToBuild();
        logTrees = other.isLogTrees();
        logRemaining = other.isLogRemaining();
        logSummary = other.isLogSummary();
        logNonManagedVisited = other.isLogNonManagedVisitied();
        logCodeRepos = other.isLogCodeRepos();
        logCodeRepoTree = other.isLogCodeRepoTree();
        includeAlreadyBuilt = other.isIncludeAlreadyBuilt();
        validateCodeRepoTags = other.isValidateCodeRepoTags();
        warnOnResolutionErrors = other.isWarnOnResolutionErrors();
        includeOptionalDeps = other.isIncludeOptionalDeps();
        gradleJava8 = other.isGradleJava8();
        gradleJavaHome = other.getGradleJavaHome();
    }

    @Override
    public Path getProjectDir() {
        return projectDir;
    }

    @Override
    public ArtifactCoords getProjectBom() {
        return projectBom;
    }

    @Override
    public Collection<ArtifactCoords> getProjectArtifacts() {
        return projectArtifacts;
    }

    @Override
    public Collection<ArtifactCoords> getIncludeArtifacts() {
        return includeArtifacts;
    }

    @Override
    public Collection<ArtifactCoords> getIncludePatterns() {
        return includePatterns;
    }

    @Override
    public Collection<ArtifactCoords> getExcludePatterns() {
        return excludePatterns;
    }

    @Override
    public boolean isIncludeNonManaged() {
        return includeNonManaged;
    }

    @Override
    public boolean isExcludeParentPoms() {
        return excludeParentPoms;
    }

    @Override
    public boolean isExcludeBomImports() {
        return excludeBomImports;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public boolean isLogArtifactsToBuild() {
        return logArtifactsToBuild;
    }

    @Override
    public boolean isLogModulesToBuild() {
        return logModulesToBuild;
    }

    @Override
    public boolean isLogTrees() {
        return logTrees;
    }

    @Override
    public boolean isLogRemaining() {
        return logRemaining;
    }

    @Override
    public boolean isLogSummary() {
        return logSummary;
    }

    @Override
    public boolean isLogNonManagedVisitied() {
        return logNonManagedVisited;
    }

    @Override
    public boolean isLogCodeRepos() {
        return logCodeRepos;
    }

    @Override
    public boolean isLogCodeRepoTree() {
        return logCodeRepoTree;
    }

    @Override
    public boolean isValidateCodeRepoTags() {
        return validateCodeRepoTags;
    }

    @Override
    public boolean isWarnOnResolutionErrors() {
        return warnOnResolutionErrors;
    }

    @Override
    public boolean isIncludeAlreadyBuilt() {
        return includeAlreadyBuilt;
    }

    @Override
    public boolean isIncludeOptionalDeps() {
        return includeOptionalDeps;
    }

    @Override
    public boolean isGradleJava8() {
        return gradleJava8;
    }

    @Override
    public String getGradleJavaHome() {
        return gradleJavaHome;
    }

    static class Builder implements ProjectDependencyConfig.Mutable {

        private Path projectDir;
        private ArtifactCoords projectBom;
        private Collection<ArtifactCoords> projectArtifacts = new ArrayList<>();
        private Collection<ArtifactCoords> includeArtifacts = new ArrayList<>();
        private Collection<ArtifactCoords> includePatterns = new ArrayList<>();
        private Collection<ArtifactCoords> excludePatterns = new ArrayList<>();
        private boolean includeNonManaged;
        private boolean excludeParentPoms;
        private boolean excludeBomImports;
        private int level = -1;
        private boolean logArtifactsToBuild;
        private boolean logModulesToBuild;
        private boolean logTrees;
        private boolean logRemaining;
        private boolean logSummary;
        private boolean logNonManagedVisited;
        private boolean logCodeRepos;
        private boolean logCodeRepoTree;
        private boolean validateCodeRepoTags;
        private boolean warnOnResolutionErrors;
        private boolean includeAlreadyBuilt;
        private boolean includeOptionalDeps;
        private boolean gradleJava8;
        private String gradleJavaHome;

        Builder() {
        }

        Builder(ProjectDependencyConfig other) {
            projectDir = other.getProjectDir();
            projectBom = other.getProjectBom();
            projectArtifacts.addAll(other.getProjectArtifacts());
            includeArtifacts.addAll(other.getIncludeArtifacts());
            includePatterns.addAll(other.getIncludePatterns());
            excludePatterns.addAll(other.getExcludePatterns());
            includeNonManaged = other.isIncludeNonManaged();
            excludeParentPoms = other.isExcludeParentPoms();
            excludeBomImports = other.isExcludeBomImports();
            level = other.getLevel();
            logArtifactsToBuild = other.isLogArtifactsToBuild();
            logModulesToBuild = other.isLogModulesToBuild();
            logTrees = other.isLogTrees();
            logRemaining = other.isLogRemaining();
            logSummary = other.isLogSummary();
            logNonManagedVisited = other.isLogNonManagedVisitied();
            logCodeRepos = other.isLogCodeRepos();
            logCodeRepoTree = other.isLogCodeRepoTree();
            validateCodeRepoTags = other.isValidateCodeRepoTags();
            warnOnResolutionErrors = other.isWarnOnResolutionErrors();
            includeAlreadyBuilt = other.isIncludeAlreadyBuilt();
            includeOptionalDeps = other.isIncludeOptionalDeps();
            gradleJava8 = other.isGradleJava8();
            gradleJavaHome = other.getGradleJavaHome();
        }

        @Override
        public Path getProjectDir() {
            return projectDir;
        }

        @Override
        public Mutable setProjectDir(Path projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        @Override
        public ArtifactCoords getProjectBom() {
            return projectBom;
        }

        @Override
        public Collection<ArtifactCoords> getProjectArtifacts() {
            return projectArtifacts;
        }

        @Override
        public Collection<ArtifactCoords> getIncludeArtifacts() {
            return includeArtifacts;
        }

        @Override
        public Collection<ArtifactCoords> getIncludePatterns() {
            return includePatterns;
        }

        @Override
        public Collection<ArtifactCoords> getExcludePatterns() {
            return excludePatterns;
        }

        @Override
        public boolean isIncludeNonManaged() {
            return includeNonManaged;
        }

        @Override
        public boolean isExcludeParentPoms() {
            return excludeParentPoms;
        }

        @Override
        public boolean isExcludeBomImports() {
            return excludeBomImports;
        }

        @Override
        public int getLevel() {
            return level;
        }

        @Override
        public boolean isLogArtifactsToBuild() {
            return logArtifactsToBuild;
        }

        @Override
        public boolean isLogModulesToBuild() {
            return logModulesToBuild;
        }

        @Override
        public boolean isLogTrees() {
            return logTrees;
        }

        @Override
        public boolean isLogRemaining() {
            return logRemaining;
        }

        @Override
        public boolean isLogSummary() {
            return logSummary;
        }

        @Override
        public boolean isLogNonManagedVisitied() {
            return logNonManagedVisited;
        }

        @Override
        public boolean isLogCodeRepos() {
            return logCodeRepos;
        }

        @Override
        public boolean isLogCodeRepoTree() {
            return logCodeRepoTree;
        }

        @Override
        public boolean isValidateCodeRepoTags() {
            return validateCodeRepoTags;
        }

        @Override
        public boolean isWarnOnResolutionErrors() {
            return warnOnResolutionErrors;
        }

        @Override
        public boolean isIncludeAlreadyBuilt() {
            return includeAlreadyBuilt;
        }

        @Override
        public boolean isIncludeOptionalDeps() {
            return includeOptionalDeps;
        }

        @Override
        public boolean isGradleJava8() {
            return gradleJava8;
        }

        @Override
        public String getGradleJavaHome() {
            return gradleJavaHome;
        }

        @Override
        public Mutable setProjectBom(ArtifactCoords bom) {
            this.projectBom = bom;
            return this;
        }

        @Override
        public Mutable setProjectArtifacts(Collection<ArtifactCoords> projectArtifacts) {
            this.projectArtifacts = projectArtifacts;
            return this;
        }

        @Override
        public Mutable setIncludeArtifacts(Set<ArtifactCoords> artifacts) {
            this.includeArtifacts = artifacts;
            return this;
        }

        @Override
        public Mutable setIncludePatterns(Set<ArtifactCoords> artifacts) {
            this.includePatterns = artifacts;
            return this;
        }

        @Override
        public Mutable addIncludePattern(ArtifactCoords artifact) {
            this.includePatterns.add(artifact);
            return this;
        }

        @Override
        public Mutable setExcludePatterns(Set<ArtifactCoords> artifacts) {
            this.excludePatterns = artifacts;
            return this;
        }

        @Override
        public Mutable addExcludePattern(ArtifactCoords artifact) {
            if (excludePatterns.isEmpty()) {
                excludePatterns = new ArrayList<>();
            }
            this.excludePatterns.add(artifact);
            return this;
        }

        @Override
        public Mutable setIncludeNonManaged(boolean includeNonManaged) {
            this.includeNonManaged = includeNonManaged;
            return this;
        }

        @Override
        public Mutable setExcludeParentPoms(boolean excludeParentPoms) {
            this.excludeParentPoms = excludeParentPoms;
            return this;
        }

        @Override
        public Mutable setExcludeBomImports(boolean excludeBomImports) {
            this.excludeBomImports = excludeBomImports;
            return this;
        }

        @Override
        public Mutable setLevel(int level) {
            this.level = level;
            return this;
        }

        @Override
        public Mutable setLogArtifactsToBuild(boolean logArtifactsToBuild) {
            this.logArtifactsToBuild = logArtifactsToBuild;
            return this;
        }

        @Override
        public Mutable setLogModulesToBuild(boolean logModulesToBuild) {
            this.logModulesToBuild = logModulesToBuild;
            return this;
        }

        @Override
        public Mutable setLogTrees(boolean logTrees) {
            this.logTrees = logTrees;
            return this;
        }

        @Override
        public Mutable setLogRemaining(boolean logRemaining) {
            this.logRemaining = logRemaining;
            return this;
        }

        @Override
        public Mutable setLogSummary(boolean logSummary) {
            this.logSummary = logSummary;
            return this;
        }

        @Override
        public Mutable setLogNonManagedVisited(boolean logNonManagedVisited) {
            this.logNonManagedVisited = logNonManagedVisited;
            return this;
        }

        @Override
        public Mutable setLogCodeRepos(boolean logCodeRepos) {
            this.logCodeRepos = logCodeRepos;
            return this;
        }

        @Override
        public Mutable setLogCodeRepoTree(boolean logCodeRepoTree) {
            this.logCodeRepoTree = logCodeRepoTree;
            return this;
        }

        @Override
        public Mutable setValidateCodeRepoTags(boolean validateTags) {
            this.validateCodeRepoTags = validateTags;
            return this;
        }

        @Override
        public Mutable setWarnOnResolutionErrors(boolean warn) {
            this.warnOnResolutionErrors = warn;
            return this;
        }

        @Override
        public Mutable setIncludeAlreadyBuilt(boolean includeAlreadyBuilt) {
            this.includeAlreadyBuilt = includeAlreadyBuilt;
            return this;
        }

        @Override
        public Mutable setIncludeOptionalDeps(boolean includeOptionalDeps) {
            this.includeOptionalDeps = includeOptionalDeps;
            return this;
        }

        @Override
        public Mutable setGradleJava8(boolean java8) {
            this.gradleJava8 = java8;
            return this;
        }

        @Override
        public Mutable setGradleJavaHome(String javaHome) {
            this.gradleJavaHome = javaHome;
            return this;
        }

        @Override
        public ProjectDependencyConfig build() {
            return new ProjectDependencyConfigImpl(this);
        }
    }

    static <T> List<T> toUnmodifiableList(Collection<T> o) {
        if (o == null || o.isEmpty()) {
            return List.of();
        }
        return List.copyOf(o);
    }
}
