package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.detector.PrefixedTagReleaseIdDetector;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

public class DependenciesToBuildReportGenerator {

    private static final String NOT_MANAGED = " [not managed]";

    public class Builder {

        private Builder() {
        }

        public Builder setResolver(MavenArtifactResolver artifactResolver) {
            resolver = artifactResolver;
            return this;
        }

        public Builder setBom(ArtifactCoords bom) {
            targetBomCoords = bom;
            return this;
        }

        public Builder setTopLevelArtifactsToBuild(Collection<ArtifactCoords> topArtifactsToBuild) {
            topLevelArtifactsToBuild = topArtifactsToBuild;
            return this;
        }

        public Builder setLevel(int level) {
            DependenciesToBuildReportGenerator.this.level = level;
            return this;
        }

        public Builder setIncludeNonManaged(boolean includeNonManaged) {
            DependenciesToBuildReportGenerator.this.includeNonManaged = includeNonManaged;
            return this;
        }

        public Builder setLogArtifactsToBuild(boolean logArtifactsToBuild) {
            DependenciesToBuildReportGenerator.this.logArtifactsToBuild = logArtifactsToBuild;
            return this;
        }

        public Builder setLogModulesToBuild(boolean logModulesToBuild) {
            DependenciesToBuildReportGenerator.this.logModulesToBuild = logModulesToBuild;
            return this;
        }

        public Builder setLogTrees(boolean logTrees) {
            DependenciesToBuildReportGenerator.this.logTrees = logTrees;
            return this;
        }

        public Builder setLogRemaining(boolean logRemaining) {
            DependenciesToBuildReportGenerator.this.logRemaining = logRemaining;
            return this;
        }

        public Builder setLogSummary(boolean logSummary) {
            DependenciesToBuildReportGenerator.this.logSummary = logSummary;
            return this;
        }

        public Builder setLogNonManagedVisited(boolean logNonManagedVisited) {
            DependenciesToBuildReportGenerator.this.logNonManagedVisited = logNonManagedVisited;
            return this;
        }

        public Builder setOutputFile(File outputFile) {
            DependenciesToBuildReportGenerator.this.outputFile = outputFile;
            return this;
        }

        public Builder setAppendOutput(boolean appendOutput) {
            DependenciesToBuildReportGenerator.this.appendOutput = appendOutput;
            return this;
        }

        public Builder setLogCodeRepos(boolean logCodeRepos) {
            DependenciesToBuildReportGenerator.this.logCodeRepos = logCodeRepos;
            return this;
        }

        public Builder setLogCodeRepoGraph(boolean logCodeRepoGraph) {
            DependenciesToBuildReportGenerator.this.logCodeRepoGraph = logCodeRepoGraph;
            return this;
        }

        public Builder setExcludeParentPoms(boolean excludeParentPoms) {
            DependenciesToBuildReportGenerator.this.excludeParentPoms = excludeParentPoms;
            return this;
        }

        public Builder setExcludeBomImports(boolean excludeBomImports) {
            DependenciesToBuildReportGenerator.this.excludeBomImports = excludeBomImports;
            return this;
        }

        public Builder setArtifactConstraintsProvider(Function<ArtifactCoords, List<Dependency>> constraintsProvider) {
            artifactConstraintsProvider = constraintsProvider;
            return this;
        }

        public Builder setMessageWriter(MessageWriter msgWriter) {
            log = msgWriter;
            return this;
        }

        public Builder setExcludeGroupIds(Set<String> groupIds) {
            excludeGroupIds = groupIds;
            return this;
        }

        public Builder setExcludeKeys(Set<ArtifactKey> artifactKeys) {
            excludeKeys = artifactKeys;
            return this;
        }

        public Builder setExcludeArtifacts(Set<ArtifactCoords> artifacts) {
            excludeArtifacts = artifacts;
            return this;
        }

        public Builder setIncludeGroupIds(Set<String> groupIds) {
            includeGroupIds = groupIds;
            return this;
        }

        public Builder setIncludeKeys(Set<ArtifactKey> artifactKeys) {
            includeKeys = artifactKeys;
            return this;
        }

        public Builder setIncludeArtifacts(Set<ArtifactCoords> artifacts) {
            includeArtifacts = artifacts;
            return this;
        }

        public Builder setValidateCodeRepoTags(boolean validateTags) {
            validateCodeRepoTags = validateTags;
            return this;
        }

        public DependenciesToBuildReportGenerator build() {
            if (resolver == null) {
                try {
                    resolver = MavenArtifactResolver.builder().setWorkspaceDiscovery(false).build();
                } catch (BootstrapMavenException e) {
                    throw new IllegalStateException("Failed to initialize the Maven artifact resolver", e);
                }
            }
            if (log == null) {
                log = MessageWriter.info();
            }
            return DependenciesToBuildReportGenerator.this;
        }

        protected DependenciesToBuildReportGenerator doBuild() {
            return DependenciesToBuildReportGenerator.this;
        }
    }

    public static Builder builder() {
        return new DependenciesToBuildReportGenerator().new Builder();
    }

    private DependenciesToBuildReportGenerator() {
    }

    private MavenArtifactResolver resolver;
    private ArtifactCoords targetBomCoords;
    private MessageWriter log;

    private Collection<ArtifactCoords> topLevelArtifactsToBuild = List.of();

    /**
     * The depth level of a dependency tree of each supported Quarkus extension to capture.
     * Setting the level to 0 will target the supported extension artifacts themselves.
     * Setting the level to 1, will target the supported extension artifacts plus their direct dependencies.
     * If the level is not specified, the default will be -1, which means all the levels.
     */
    private int level = -1;

    /**
     * Whether to exclude dependencies (and their transitive dependencies) that aren't managed in the BOM. The default is true.
     */
    private boolean includeNonManaged;

    /**
     * Whether to log the coordinates of the artifacts captured down to the depth specified. The default is true.
     */
    private boolean logArtifactsToBuild = true;

    /**
     * Whether to log the module GAVs the artifacts to be built belongs to instead of all
     * the complete artifact coordinates to be built.
     * If this option is enabled, it overrides {@link #logArtifactsToBuild}
     */
    private boolean logModulesToBuild;

    /**
     * Whether to log the dependency trees walked down to the depth specified. The default is false.
     */
    private boolean logTrees;

    /**
     * Whether to log the coordinates of the artifacts below the depth specified. The default is false.
     */
    private boolean logRemaining;

    /**
     * Whether to log the summary at the end. The default is true.
     */
    private boolean logSummary = true;

    /**
     * Whether to log the summary at the end. The default is true.
     */
    private boolean logNonManagedVisited;

    /**
     * If specified, this parameter will cause the output to be written to the path specified, instead of writing to
     * the console.
     */
    private File outputFile;
    private PrintStream fileOutput;

    /**
     * Whether to append outputs into the output file or overwrite it.
     */
    private boolean appendOutput;

    /*
     * Whether to log code repository info for the artifacts to be built from source
     */
    private boolean logCodeRepos;

    /*
     * Whether to log code repository dependency graph.
     */
    private boolean logCodeRepoGraph;

    /*
     * Whether to exclude parent POMs from the list of artifacts to be built from source
     */
    private boolean excludeParentPoms;

    /*
     * Whether to exclude BOMs imported in the POMs of artifacts to be built from the list of artifacts to be built from source
     */
    private boolean excludeBomImports;

    /*
     * Whether to validate the discovered code repo and tags that are included in the report
     */
    private boolean validateCodeRepoTags;

    /*
     * Whether to include test JARs
     */
    private boolean includeTestJars;

    private Set<String> excludeGroupIds = Set.of();
    private Set<ArtifactKey> excludeKeys = Set.of();
    private Set<ArtifactCoords> excludeArtifacts = Set.of();
    private Set<String> includeGroupIds = Set.of();
    private Set<ArtifactKey> includeKeys = Set.of();
    private Set<ArtifactCoords> includeArtifacts = Set.of();

    private Function<ArtifactCoords, List<Dependency>> artifactConstraintsProvider;
    private Set<ArtifactCoords> targetBomConstraints;
    private List<Dependency> targetBomManagedDeps;
    private final Set<ArtifactCoords> allDepsToBuild = new HashSet<>();
    private final Set<ArtifactCoords> nonManagedVisited = new HashSet<>();
    private final Set<ArtifactCoords> skippedDeps = new HashSet<>();
    private final Set<ArtifactCoords> remainingDeps = new HashSet<>();

    private final Map<ArtifactCoords, ArtifactDependency> artifactDeps = new HashMap<>();
    private final Map<ReleaseId, ReleaseRepo> releaseRepos = new HashMap<>();
    private final Map<ArtifactCoords, Map<String, String>> effectivePomProps = new HashMap<>();

    private final Map<Set<ReleaseId>, List<ReleaseId>> circularRepoDeps = new HashMap<>();

    private final boolean excludeProductizedDeps = true;

    public void generate() {

        if (logCodeRepoGraph) {
            logCodeRepos = true;
        }

        targetBomManagedDeps = getBomConstraints(targetBomCoords);
        targetBomConstraints = new HashSet<>(targetBomManagedDeps.size());
        for (Dependency d : targetBomManagedDeps) {
            targetBomConstraints.add(toCoords(d.getArtifact()));
        }
        if (artifactConstraintsProvider == null) {
            artifactConstraintsProvider = t -> targetBomManagedDeps;
        }

        for (ArtifactCoords coords : getTopLevelArtifactsToBuild()) {
            if (isExcluded(coords)) {
                continue;
            }
            processTopLevelArtifact(artifactConstraintsProvider.apply(coords), coords);
        }

        if (!includeArtifacts.isEmpty()) {
            // this is a tricky case, these artifacts will be resolved against all the member BOMs
            // we may want to have them configured per member instead of in the global config
            for (ArtifactCoords coords : includeArtifacts) {
                processTopLevelArtifact(artifactConstraintsProvider.apply(coords), coords);
            }
        }

        if (excludeProductizedDeps) {
            removeProductizedDeps();
        }

        try {
            int codeReposTotal = 0;
            if (logArtifactsToBuild && !allDepsToBuild.isEmpty()) {
                logComment("Artifacts to be built from source from "
                        + (targetBomCoords == null ? "" : targetBomCoords.toCompactCoords()) + ":");
                if (logCodeRepos) {
                    initReleaseRepos();
                    detectCircularRepoDeps();
                    codeReposTotal = releaseRepos.size();

                    final List<ReleaseRepo> sorted = new ArrayList<>(codeReposTotal);
                    for (ReleaseRepo r : releaseRepos.values()) {
                        if (r.isRoot()) {
                            sort(r, new HashSet<>(codeReposTotal), sorted);
                        }
                    }

                    for (ReleaseRepo e : sorted) {
                        logComment("repo-url " + e.id().origin());
                        logComment("tag " + e.id().version().asString());
                        for (String s : toSortedStrings(e.artifacts, logModulesToBuild)) {
                            log(s);
                        }
                    }

                    if (!circularRepoDeps.isEmpty()) {
                        logComment("ERROR: The following circular dependency chains were detected among releases:");
                        final Iterator<List<ReleaseId>> chains = circularRepoDeps.values().iterator();
                        int i = 0;
                        while (chains.hasNext()) {
                            logComment("  Chain #" + ++i + ":");
                            chains.next().forEach(id -> logComment("    " + id));
                            logComment("");
                        }
                    }
                    if (logCodeRepoGraph) {
                        logComment("");
                        logComment("Code repository dependency graph");
                        for (ReleaseRepo r : releaseRepos.values()) {
                            if (r.isRoot()) {
                                logReleaseRepoDep(r, 0);
                            }
                        }
                        logComment("");
                    }

                } else {
                    for (String s : toSortedStrings(allDepsToBuild, logModulesToBuild)) {
                        log(s);
                    }
                }
            }

            if (logNonManagedVisited && !nonManagedVisited.isEmpty()) {
                logComment("Non-managed dependencies visited walking dependency trees:");
                final List<String> sorted = toSortedStrings(nonManagedVisited, logModulesToBuild);
                for (int i = 0; i < sorted.size(); ++i) {
                    logComment((i + 1) + ") " + sorted.get(i));
                }
            }

            if (logRemaining) {
                logComment("Remaining artifacts include:");
                final List<String> sorted = toSortedStrings(remainingDeps, logModulesToBuild);
                for (int i = 0; i < sorted.size(); ++i) {
                    logComment((i + 1) + ") " + sorted.get(i));
                }
            }

            if (logSummary) {
                final StringBuilder sb = new StringBuilder().append("Selecting ");
                if (this.level < 0) {
                    sb.append("all the");
                } else {
                    sb.append(this.level).append(" level(s) of");
                }
                if (this.includeNonManaged) {
                    sb.append(" managed and non-managed");
                } else {
                    sb.append(" managed (stopping at the first non-managed one)");
                }
                sb.append(" dependencies of supported extensions");
                if (targetBomCoords != null) {
                    sb.append(" from ").append(targetBomCoords.toCompactCoords());
                }
                sb.append(" will result in:");
                logComment(sb.toString());

                sb.setLength(0);
                sb.append(allDepsToBuild.size()).append(" artifacts");
                if (codeReposTotal > 0) {
                    sb.append(" from ").append(codeReposTotal).append(" code repositories");
                }
                sb.append(" to build from source");
                logComment(sb.toString());
                if (includeNonManaged && !nonManagedVisited.isEmpty()) {
                    logComment("  * " + nonManagedVisited.size() + " of which is/are not managed by the BOM");
                }
                if (!skippedDeps.isEmpty()) {
                    logComment(skippedDeps.size() + " dependency nodes skipped");
                }
                logComment((allDepsToBuild.size() + skippedDeps.size()) + " dependencies visited in total");
            }
        } finally {
            if (fileOutput != null) {
                fileOutput.close();
            }
        }
    }

    private void removeProductizedDeps() {
        final Set<ArtifactKey> alreadyBuiltKeys = allDepsToBuild.stream()
                .filter(c -> RhVersionPattern.isRhVersion(c.getVersion()))
                .map(ArtifactCoords::getKey).collect(Collectors.toSet());
        if (!alreadyBuiltKeys.isEmpty()) {
            Iterator<ArtifactCoords> i = allDepsToBuild.iterator();
            while (i.hasNext()) {
                final ArtifactCoords coords = i.next();
                if (alreadyBuiltKeys.contains(coords.getKey())) {
                    i.remove();
                    artifactDeps.remove(coords);
                    artifactDeps.values().forEach(d -> {
                        d.removeDependency(coords);
                    });
                }
            }
        }
    }

    protected Iterable<ArtifactCoords> getTopLevelArtifactsToBuild() {
        if (topLevelArtifactsToBuild == null || topLevelArtifactsToBuild.isEmpty()) {
            final List<ArtifactCoords> result = new ArrayList<>();
            for (ArtifactCoords d : targetBomConstraints) {
                if (targetBomCoords.getGroupId().equals(d.getGroupId()) && d.isJar() && !isExcluded(d)) {
                    result.add(d);
                    log.debug(d.toCompactCoords() + " selected as a top level artifact to build");
                }
            }
            return result;
        }
        return topLevelArtifactsToBuild;
    }

    private void processTopLevelArtifact(List<Dependency> managedDeps, ArtifactCoords topLevelArtifact) {
        final DependencyNode root;
        try {
            final Artifact a = toAetherArtifact(topLevelArtifact);
            root = resolver.getSystem().collectDependencies(resolver.getSession(), new CollectRequest()
                    .setManagedDependencies(managedDeps)
                    .setRepositories(resolver.getRepositories())
                    .setRoot(new Dependency(a, JavaScopes.RUNTIME)))
                    .getRoot();
            // if the dependencies are not found, make sure the artifact actually exists
            if (root.getChildren().isEmpty()) {
                resolver.resolve(a);
            }
        } catch (Exception e1) {
            throw new RuntimeException("Failed to collect dependencies of " + topLevelArtifact.toCompactCoords(), e1);
        }

        if (logTrees) {
            if (targetBomConstraints.contains(topLevelArtifact)) {
                logComment(topLevelArtifact.toCompactCoords());
            } else {
                logComment(topLevelArtifact.toCompactCoords() + NOT_MANAGED);
            }
        }

        final boolean addDependency;
        try {
            addDependency = addDependencyToBuild(topLevelArtifact);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process " + topLevelArtifact, e);
        }
        if (addDependency) {
            final ArtifactDependency extDep = getOrCreateArtifactDep(topLevelArtifact);
            if (!excludeParentPoms && logTrees) {
                extDep.logBomImportsAndParents();
            }
            for (DependencyNode d : root.getChildren()) {
                processNodes(extDep, d, 1, false);
            }
        } else if (logRemaining) {
            for (DependencyNode d : root.getChildren()) {
                processNodes(null, d, 1, true);
            }
        }

        if (logTrees) {
            logComment("");
        }
    }

    private static DefaultArtifact toAetherArtifact(ArtifactCoords a) {
        return new DefaultArtifact(a.getGroupId(),
                a.getArtifactId(), a.getClassifier(),
                a.getType(), a.getVersion());
    }

    private void initReleaseRepos() {

        final ReleaseIdResolver idResolver = newReleaseIdResolver(resolver, log, validateCodeRepoTags,
                getRhCoordsUpstreamVersions());

        final Map<ArtifactCoords, ReleaseId> artifactReleases = new HashMap<>();
        for (ArtifactCoords c : allDepsToBuild) {
            final ReleaseId releaseId;
            try {
                releaseId = idResolver.releaseId(toAetherArtifact(c));
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve release id for " + c, e);
            }
            getOrCreateRepo(releaseId).artifacts.add(c);
            artifactReleases.put(c, releaseId);
        }

        final Iterator<Map.Entry<ReleaseId, ReleaseRepo>> i = releaseRepos.entrySet().iterator();
        while (i.hasNext()) {
            if (i.next().getValue().artifacts.isEmpty()) {
                i.remove();
            }
        }
        for (ArtifactDependency d : artifactDeps.values()) {
            final ReleaseRepo repo = getRepo(artifactReleases.get(d.coords));
            for (ArtifactDependency c : d.getAllDependencies()) {
                repo.addRepoDependency(getRepo(artifactReleases.get(c.coords)));
            }
        }
    }

    private Map<ArtifactCoords, String> getRhCoordsUpstreamVersions() {
        if (excludeProductizedDeps) {
            // already excluded
            return Map.of();
        }
        final Map<String, List<ArtifactVersion>> upstreamVersions = new HashMap<>();
        final List<ArtifactCoords> rhCoords = new ArrayList<>();
        for (ArtifactCoords c : allDepsToBuild) {
            if (RhVersionPattern.isRhVersion(c.getVersion())) {
                rhCoords.add(c);
            } else {
                upstreamVersions.computeIfAbsent(c.getGroupId(), k -> new ArrayList<>())
                        .add(new DefaultArtifactVersion(c.getVersion()));
            }
        }
        if (rhCoords.isEmpty()) {
            return Map.of();
        }
        final Map<ArtifactCoords, String> rhCoordsUpstreamVersions = new HashMap<>(rhCoords.size());
        for (ArtifactCoords c : rhCoords) {
            final List<ArtifactVersion> originalVersions = upstreamVersions.get(c.getGroupId());
            if (originalVersions == null) {
                continue;
            }
            final ArtifactVersion noRhSuffixVersion = new DefaultArtifactVersion(
                    RhVersionPattern.ensureNoRhSuffix(c.getVersion()));
            for (ArtifactVersion v : originalVersions) {
                if (v.equals(noRhSuffixVersion)) {
                    rhCoordsUpstreamVersions.put(c, v.toString());
                    break;
                }
            }
        }
        return rhCoordsUpstreamVersions;
    }

    private static ReleaseIdResolver newReleaseIdResolver(MavenArtifactResolver artifactResolver, MessageWriter log,
            boolean validateCodeRepoTags, Map<ArtifactCoords, String> versionMapping) {
        final List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
        releaseDetectors.add(new PrefixedTagReleaseIdDetector("jetty-", List.of("org.eclipse.jetty")));
        releaseDetectors.add(
                // Vert.X
                new ReleaseIdDetector() {

                    final Set<String> artifactIdRepos = Set.of("vertx-service-proxy",
                            "vertx-amqp-client",
                            "vertx-health-check",
                            "vertx-camel-bridge",
                            "vertx-redis-client",
                            "vertx-json-schema",
                            "vertx-lang-groovy",
                            "vertx-mail-client",
                            "vertx-http-service-factory",
                            "vertx-tcp-eventbus-bridge",
                            "vertx-dropwizard-metrics",
                            "vertx-consul-client",
                            "vertx-maven-service-factory",
                            "vertx-cassandra-client",
                            "vertx-circuit-breaker",
                            "vertx-jdbc-client",
                            "vertx-reactive-streams",
                            "vertx-rabbitmq-client",
                            "vertx-mongo-client",
                            "vertx-sockjs-service-proxy",
                            "vertx-kafka-client",
                            "vertx-micrometer-metrics",
                            "vertx-service-factory");

                    @Override
                    public ReleaseId detectReleaseId(ReleaseIdResolver releaseResolver, Artifact artifact)
                            throws BomDecomposerException {
                        if (!"io.vertx".equals(artifact.getGroupId())) {
                            return null;
                        }
                        String s = artifact.getArtifactId();
                        if (!s.startsWith("vertx-")) {
                            return releaseResolver.defaultReleaseId(artifact);
                        }
                        if (s.equals("vertx-uri-template")
                                || s.equals("vertx-codegen")
                                || s.equals("vertx-http-proxy")) {
                            return ReleaseIdFactory.forScmAndTag("https://github.com/eclipse-vertx/" + s,
                                    artifact.getVersion());
                        }
                        if (s.equals("vertx-core")) {
                            return ReleaseIdFactory.forScmAndTag("https://github.com/eclipse-vertx/vert.x",
                                    artifact.getVersion());
                        }
                        if (s.startsWith("vertx-tracing")
                                || s.equals("vertx-opentelemetry")
                                || s.equals("vertx-opentracing")
                                || s.equals("vertx-zipkin")) {
                            return ReleaseIdFactory.forScmAndTag("https://github.com/eclipse-vertx/vertx-tracing",
                                    artifact.getVersion());
                        }
                        var defaultReleaseId = releaseResolver.defaultReleaseId(artifact);
                        if (defaultReleaseId.origin().toString().endsWith("vertx-sql-client")) {
                            return defaultReleaseId;
                        }

                        if (s.startsWith("vertx-ext")) {
                            s = "vertx-ext-parent";
                        } else if (artifactIdRepos.contains(s)) {
                            // keep the artifactId
                        } else if (s.startsWith("vertx-lang-kotlin")) {
                            s = "vertx-lang-kotlin";
                        } else if (s.startsWith("vertx-service-discovery")) {
                            s = "vertx-service-discovery";
                        } else if (s.equals("vertx-template-engines")) {
                            s = "vertx-web";
                        } else if (s.equals("vertx-web-sstore-infinispan")) {
                            s = "vertx-infinispan";
                        } else if (!s.equals("vertx-bridge-common")) {
                            int i = s.indexOf('-', "vertx-".length());
                            if (i > 0) {
                                s = s.substring(0, i);
                            }
                        }

                        return ReleaseIdFactory.forScmAndTag("https://github.com/vert-x3/" + s, artifact.getVersion());
                    }
                });
        releaseDetectors
                .addAll(ServiceLoader.load(ReleaseIdDetector.class).stream().map(p -> p.get()).collect(Collectors.toList()));

        return new ReleaseIdResolver(artifactResolver, releaseDetectors, log, validateCodeRepoTags, versionMapping);
    }

    public static void main(String[] args) throws Exception {

        DependenciesToBuildReportGenerator.builder()
                .setTopLevelArtifactsToBuild(List.of(
                        ArtifactCoords.jar("org.apache.kafka", "kafka-clients", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-api", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-basic-auth-extension", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-file", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-json", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-mirror", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-mirror-client", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-runtime", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "connect-transforms", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka_2.13", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "generator", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-log4j-appender", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-metadata", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-raft", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-server-common", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-shell", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-storage", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-storage-api", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-streams", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "kafka-tools", "3.3.1"),
                        ArtifactCoords.jar("org.apache.kafka", "trogdor", "3.3.1")))
                .setIncludeNonManaged(true)
                .setLogCodeRepos(true)
                .setLogTrees(true)
                .build()
                .generate();

        /*
         * ReleaseIdResolver idResolver = newReleaseIdResolver(MavenArtifactResolver.builder().build(), MessageWriter.info(),
         * true);
         * ReleaseId releaseId = idResolver
         * .releaseId(new DefaultArtifact("org.ow2.asm", "asm", "pom", "9.3"));
         * System.out.println("RELEASE ID " + releaseId);
         */
    }

    private void sort(ReleaseRepo repo, Set<ReleaseId> processed, List<ReleaseRepo> sorted) {
        if (!processed.add(repo.id)) {
            return;
        }
        for (ReleaseRepo d : repo.dependencies.values()) {
            sort(d, processed, sorted);
        }
        sorted.add(repo);
    }

    private void logReleaseRepoDep(ReleaseRepo repo, int depth) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; ++i) {
            sb.append("  ");
        }
        sb.append(repo.id().origin()).append(' ').append(repo.id().version());
        logComment(sb.toString());
        for (ReleaseRepo child : repo.dependencies.values()) {
            logReleaseRepoDep(child, depth + 1);
        }
    }

    private static List<String> toSortedStrings(Collection<ArtifactCoords> coords, boolean asModules) {
        final List<String> list;
        if (asModules) {
            final Set<String> set = new HashSet<>();
            for (ArtifactCoords c : coords) {
                set.add(c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getVersion());
            }
            list = new ArrayList<>(set);
        } else {
            list = new ArrayList<>(coords.size());
            for (ArtifactCoords c : coords) {
                list.add(c.toGACTVString());
            }
        }
        Collections.sort(list);
        return list;
    }

    private PrintStream getOutput() {
        if (outputFile == null) {
            return System.out;
        }
        if (fileOutput == null) {
            outputFile.getParentFile().mkdirs();
            try {
                fileOutput = new PrintStream(new FileOutputStream(outputFile, appendOutput), false);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Failed to open " + outputFile + " for writing", e);
            }
        }
        return fileOutput;
    }

    private void logComment(String msg) {
        log("# " + msg);
    }

    private void log(String msg) {
        getOutput().println(msg);
    }

    private void processNodes(ArtifactDependency parent, DependencyNode node, int level, boolean remaining) {
        final ArtifactCoords coords = toCoords(node.getArtifact());
        if (isExcluded(coords)) {
            return;
        }
        ArtifactDependency artDep = null;
        if (remaining) {
            addToRemaining(coords);
        } else if (this.level < 0 || level <= this.level) {
            if (addDependencyToBuild(coords)) {
                if (logTrees) {
                    final StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < level; ++i) {
                        buf.append("  ");
                    }
                    buf.append(coords.toCompactCoords());
                    if (!targetBomConstraints.contains(coords)) {
                        buf.append(' ').append(NOT_MANAGED);
                    }
                    logComment(buf.toString());
                }
                if (parent != null) {
                    artDep = getOrCreateArtifactDep(coords);
                    parent.addDependency(artDep);
                    if (logTrees) {
                        artDep.logBomImportsAndParents(level + 1);
                    }
                }
            } else if (logRemaining) {
                remaining = true;
            } else {
                return;
            }
        } else {
            addToSkipped(coords);
            if (logRemaining) {
                remaining = true;
                addToRemaining(coords);
            } else {
                return;
            }
        }
        for (DependencyNode child : node.getChildren()) {
            processNodes(artDep, child, level + 1, remaining);
        }
    }

    private boolean addDependencyToBuild(ArtifactCoords coords) {
        if (!addArtifactToBuild(coords)) {
            return false;
        }
        if (!excludeParentPoms) {
            addImportedBomsAndParentPomToBuild(coords);
        }
        return true;
    }

    private boolean addArtifactToBuild(ArtifactCoords coords) {
        final boolean managed = targetBomConstraints.contains(coords);
        if (!managed) {
            nonManagedVisited.add(coords);
        }

        if (managed || includeNonManaged || isIncluded(coords)
                || !excludeParentPoms && coords.getType().equals(ArtifactCoords.TYPE_POM)) {
            allDepsToBuild.add(coords);
            skippedDeps.remove(coords);
            remainingDeps.remove(coords);
            return true;
        }

        addToSkipped(coords);
        if (logRemaining) {
            addToRemaining(coords);
        }
        return false;
    }

    private Map<String, String> addImportedBomsAndParentPomToBuild(ArtifactCoords coords) {
        final ArtifactCoords pomCoords = coords.getType().equals(ArtifactCoords.TYPE_POM) ? coords
                : ArtifactCoords.pom(coords.getGroupId(), coords.getArtifactId(), coords.getVersion());
        if (allDepsToBuild.contains(pomCoords)) {
            return effectivePomProps.getOrDefault(pomCoords, Map.of());
        }
        final Path pomXml;
        try {
            pomXml = resolver.resolve(toAetherArtifact(pomCoords)).getArtifact().getFile().toPath();
        } catch (BootstrapMavenException e) {
            throw new IllegalStateException("Failed to resolve " + pomCoords, e);
        }
        final Model model;
        try {
            model = ModelUtils.readModel(pomXml);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + pomXml, e);
        }
        final ArtifactDependency artDep = getOrCreateArtifactDep(coords);
        Map<String, String> parentPomProps = null;
        final Parent parent = model.getParent();
        if (parent != null) {
            String parentVersion = parent.getVersion();
            if (ModelUtils.isUnresolvedVersion(parentVersion)) {
                if (model.getVersion() == null || model.getVersion().equals(parentVersion)) {
                    parentVersion = pomCoords.getVersion();
                } else {
                    log.warn("Failed to resolve the version of" + parent.getGroupId() + ":" + parent.getArtifactId() + ":"
                            + parent.getVersion() + " as a parent of " + pomCoords);
                    parentVersion = null;
                }
            }
            if (parentVersion != null) {
                final ArtifactCoords parentPomCoords = ArtifactCoords.pom(parent.getGroupId(), parent.getArtifactId(),
                        parentVersion);
                if (!isExcluded(parentPomCoords)) {
                    artDep.setParentPom(getOrCreateArtifactDep(parentPomCoords));
                    parentPomProps = addImportedBomsAndParentPomToBuild(parentPomCoords);
                    addArtifactToBuild(parentPomCoords);
                }
            }
        }

        if (excludeBomImports) {
            return Map.of();
        }
        Map<String, String> pomProps = toMap(model.getProperties());
        pomProps.put("project.version", pomCoords.getVersion());
        pomProps.put("project.groupId", pomCoords.getGroupId());
        if (parentPomProps != null) {
            final Map<String, String> tmp = new HashMap<>(parentPomProps.size() + pomProps.size());
            tmp.putAll(parentPomProps);
            tmp.putAll(pomProps);
            pomProps = tmp;
        }
        effectivePomProps.put(pomCoords, pomProps);
        addImportedBomsToBuild(artDep, model, pomProps);
        return pomProps;
    }

    private void addImportedBomsToBuild(ArtifactDependency pomArtDep, Model model, Map<String, String> effectiveProps) {
        final DependencyManagement dm = model.getDependencyManagement();
        if (dm == null) {
            return;
        }
        for (org.apache.maven.model.Dependency d : dm.getDependencies()) {
            if ("import".equals(d.getScope()) && ArtifactCoords.TYPE_POM.equals(d.getType())) {
                final String groupId = resolveProperty(d.getGroupId(), d, effectiveProps);
                final String version = resolveProperty(d.getVersion(), d, effectiveProps);
                if (groupId == null || version == null) {
                    continue;
                }
                final ArtifactCoords bomCoords = ArtifactCoords.pom(groupId, d.getArtifactId(), version);
                if (!isExcluded(bomCoords)) {
                    if (pomArtDep != null) {
                        final ArtifactDependency bomDep = getOrCreateArtifactDep(bomCoords);
                        pomArtDep.addBomImport(bomDep);
                    }
                    addImportedBomsAndParentPomToBuild(bomCoords);
                    addArtifactToBuild(bomCoords);
                }
            }
        }
    }

    private String resolveProperty(String expr, org.apache.maven.model.Dependency dep, Map<String, String> props) {
        if (expr.startsWith("${") && expr.endsWith("}")) {
            final String name = expr.substring(2, expr.length() - 1);
            final String value = props.get(name);
            if (value == null) {
                log.warn("Failed to resolve " + value + " from " + dep);
                return null;
            }
            return resolveProperty(value, dep, props);
        }
        return expr;
    }

    private void addToSkipped(ArtifactCoords coords) {
        if (!allDepsToBuild.contains(coords)) {
            skippedDeps.add(coords);
        }
    }

    private void addToRemaining(ArtifactCoords coords) {
        if (!allDepsToBuild.contains(coords)) {
            remainingDeps.add(coords);
        }
    }

    private boolean isExcluded(ArtifactCoords coords) {
        return excludeGroupIds.contains(coords.getGroupId())
                || excludeKeys.contains(coords.getKey())
                || excludeArtifacts.contains(coords)
                || !includeTestJars && coords.getClassifier().equals("tests");
    }

    private boolean isIncluded(ArtifactCoords coords) {
        return includeGroupIds.contains(coords.getGroupId())
                || includeKeys.contains(coords.getKey())
                || includeArtifacts.contains(coords);
    }

    private List<Dependency> getBomConstraints(ArtifactCoords bomCoords) {
        if (bomCoords == null) {
            return List.of();
        }
        final Artifact bomArtifact = new DefaultArtifact(bomCoords.getGroupId(), bomCoords.getArtifactId(),
                ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, bomCoords.getVersion());
        List<Dependency> managedDeps;
        try {
            managedDeps = resolver.resolveDescriptor(bomArtifact)
                    .getManagedDependencies();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to resolve the descriptor of " + bomCoords, e);
        }
        if (managedDeps.isEmpty()) {
            throw new RuntimeException(bomCoords.toCompactCoords()
                    + " does not include any managed dependency or its descriptor could not be read");
        }
        return managedDeps;
    }

    private ArtifactDependency getOrCreateArtifactDep(ArtifactCoords c) {
        return artifactDeps.computeIfAbsent(c, k -> new ArtifactDependency(c));
    }

    private class ArtifactDependency {
        final ArtifactCoords coords;
        final Map<ArtifactCoords, ArtifactDependency> children = new LinkedHashMap<>();
        final Map<ArtifactCoords, ArtifactDependency> bomImports = new LinkedHashMap<>();
        ArtifactDependency parentPom;

        ArtifactDependency(ArtifactCoords coords) {
            this.coords = coords;
        }

        public void addBomImport(ArtifactDependency bomDep) {
            bomImports.put(bomDep.coords, bomDep);
        }

        public void setParentPom(ArtifactDependency parentPom) {
            this.parentPom = parentPom;
        }

        void addDependency(ArtifactDependency d) {
            children.putIfAbsent(d.coords, d);
        }

        Iterable<ArtifactDependency> getAllDependencies() {
            final List<ArtifactDependency> list = new ArrayList<>(children.size() + bomImports.size() + 1);
            if (parentPom != null) {
                list.add(parentPom);
            }
            list.addAll(bomImports.values());
            list.addAll(children.values());
            return list;
        }

        private void removeDependency(ArtifactCoords coords) {
            if (children.remove(coords) != null) {
                return;
            }
            if (bomImports.remove(coords) != null) {
                return;
            }
            if (parentPom != null && parentPom.coords.equals(coords)) {
                parentPom = null;
            }
        }

        private void logBomImportsAndParents() {
            logBomImportsAndParents(1);
        }

        private void logBomImportsAndParents(int depth) {
            if (parentPom == null && bomImports.isEmpty()) {
                return;
            }
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; ++i) {
                sb.append("  ");
            }
            final String offset = sb.toString();
            if (parentPom != null) {
                sb.setLength(0);
                sb.append(offset).append(parentPom.coords.toCompactCoords()).append(" [parent pom]");
                logComment(sb.toString());
                parentPom.logBomImportsAndParents(depth + 1);
            }
            for (ArtifactDependency d : bomImports.values()) {
                sb.setLength(0);
                sb.append(offset).append(d.coords.toCompactCoords()).append(" [bom import]");
                logComment(sb.toString());
                d.logBomImportsAndParents(depth + 1);
            }
        }
    }

    private ReleaseRepo getOrCreateRepo(ReleaseId id) {
        return releaseRepos.computeIfAbsent(id, k -> new ReleaseRepo(id));
    }

    private ReleaseRepo getRepo(ReleaseId id) {
        return Objects.requireNonNull(releaseRepos.get(id));
    }

    private void detectCircularRepoDeps() {
        for (ReleaseRepo r : releaseRepos.values()) {
            final List<ReleaseId> chain = new ArrayList<>();
            detectCircularRepoDeps(r, chain);
        }
    }

    private void detectCircularRepoDeps(ReleaseRepo r, List<ReleaseId> chain) {
        if (chain.contains(r.id)) {
            chain.add(r.id);
            circularRepoDeps.computeIfAbsent(new HashSet<>(chain), k -> new ArrayList<>(chain));
            chain.remove(chain.size() - 1);
            return;
        }
        chain.add(r.id);
        for (ReleaseRepo d : r.dependencies.values()) {
            detectCircularRepoDeps(d, chain);
        }
        chain.remove(chain.size() - 1);
    }

    private static class ReleaseRepo {

        final ReleaseId id;
        final List<ArtifactCoords> artifacts = new ArrayList<>();
        final Map<ReleaseId, ReleaseRepo> dependants = new HashMap<>();
        final Map<ReleaseId, ReleaseRepo> dependencies = new LinkedHashMap<>();

        ReleaseRepo(ReleaseId release) {
            this.id = release;
        }

        ReleaseId id() {
            return id;
        }

        void addRepoDependency(ReleaseRepo repo) {
            if (repo != this) {
                dependencies.putIfAbsent(repo.id(), repo);
                repo.dependants.putIfAbsent(id(), this);
            }
        }

        boolean isRoot() {
            return dependants.isEmpty();
        }
    }

    private static Map<String, String> toMap(Properties props) {
        final Map<String, String> map = new HashMap<>(props.size());
        for (Map.Entry<?, ?> e : props.entrySet()) {
            map.put(toString(e.getKey()), toString(e.getValue()));
        }
        return map;
    }

    private static String toString(Object o) {
        return o == null ? null : o.toString();
    }

    private static ArtifactCoords toCoords(Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }
}
