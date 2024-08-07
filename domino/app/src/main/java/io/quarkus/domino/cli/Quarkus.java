package io.quarkus.domino.cli;

import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.ArtifactCoordsPattern;
import io.quarkus.domino.ArtifactSet;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.domino.inspect.DependencyTreeError;
import io.quarkus.domino.inspect.DependencyTreeInspector;
import io.quarkus.domino.inspect.DependencyTreeVisitor;
import io.quarkus.domino.inspect.quarkus.QuarkusPlatformInfo;
import io.quarkus.domino.inspect.quarkus.QuarkusPlatformInfoReader;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.paths.PathTree;
import io.quarkus.util.GlobUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import picocli.CommandLine;

@CommandLine.Command(name = "quarkus", header = "Quarkus platform release analysis", description = "%n"
        + "Various options to analyze dependencies of a Quarkus platform release.")
public class Quarkus implements Callable<Integer> {

    private static final String MEMBER_HEADER_PREFIX = "= ";
    private static final String BOM_ENTRY_HEADER_PREFIX = "== ";
    private static final String EXTENSIONS_HEADER_PREFIX = "== ";

    private static final List<ArtifactKey> EXTRA_CORE_ARTIFACTS = List.of(
            ArtifactKey.of("io.quarkus", "quarkus-junit5", ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_JAR),
            ArtifactKey.of("io.quarkus", "quarkus-junit5-mockito", ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_JAR));

    @CommandLine.Option(names = {
            "--settings",
            "-s" }, description = "A path to Maven settings that should be used when initializing the Maven resolver")
    protected String settings;

    @CommandLine.Option(names = {
            "--maven-profiles",
            "-P" }, description = "Comma-separated list of Maven profiles that should be enabled when resolving dependencies")
    public String mavenProfiles;

    @CommandLine.Option(names = { "--repo-dir" }, description = "Local repository directory")
    public String repoDir;

    @CommandLine.Option(names = { "--version" }, description = "Local repository directory")
    public String version;

    @CommandLine.Option(names = { "--platform-group-id" }, description = "Quarkus platform groupId")
    public String platformGroupId;

    @CommandLine.Option(names = { "--parallel" }, description = "Resolves dependency trees in parallel", defaultValue = "true")
    public boolean parallelProcessing;

    @CommandLine.Option(names = {
            "--trace" }, description = "Trace artifacts matching specified glob patterns as dependencies", split = ",")
    protected List<String> trace = List.of();

    @CommandLine.Option(names = {
            "--tree" }, description = "Log complete dependency tree for traced artifacts")
    public boolean tree;

    @CommandLine.Option(names = {
            "--resolve" }, description = "Resolve binary artifacts in addition to collecting their metadata")
    public boolean resolve;

    @CommandLine.Option(names = {
            "--extension-versions" }, description = "Limit extension versions to those matching specified glob patterns", split = ",")
    protected List<String> extensionVersions = List.of();
    private List<Pattern> extensionVersionPatterns;

    @CommandLine.Option(names = {
            "--runtime-only" }, description = "Whether to limit extension artifacts to runtime only")
    public boolean runtimeOnly;

    @CommandLine.Option(names = {
            "--members" }, description = "Limit the analysis to the specified members", split = ",")
    protected Set<String> members = Set.of();

    @CommandLine.Option(names = {
            "--redhat-version-rate" }, description = "Calculate the rate of redhat versions among the inspected dependencies")
    public boolean redhatVersionRate;

    @CommandLine.Option(names = {
            "--log-not-matched" }, description = "Provide more details in combination with other options", defaultValue = "false")
    public boolean logNotMatched;

    @CommandLine.Option(names = {
            "--info" }, description = "Log basic Quarkus platform release information")
    public boolean info;

    protected MessageWriter log = MessageWriter.info();

    @Override
    public Integer call() throws Exception {

        var resolver = getResolver();
        var platform = readPlatformInfo(resolver);
        if (info) {
            log.info("");
            log.info("Platform version: " + version);
            log.info("Quarkus core version: " + platform.getCore().getQuarkusCoreVersion());
            log.info("Maven plugin: " + platform.getMavenPlugin().toCompactCoords());
            log.info("");
            log.info("Member BOMs:");
            for (var m : platform.getMembers()) {
                log.info("- " + m.getBom().toCompactCoords());
            }
            log.info("");
            return 0;
        }

        var memberReports = new ArrayList<MemberReport>(members.isEmpty() ? platform.getMembers().size() : members.size());
        final MemberReport coreReport = new MemberReport(platform.getCore(), isMemberSelected(platform.getCore()));
        for (var m : platform.getMembers()) {
            if (isMemberSelected(m) || m == platform.getCore()) {
                memberReports.add(m == platform.getCore() ? coreReport : new MemberReport(m));
            }
        }

        final ArtifactSet tracePattern = initTracePattern();
        final Map<ArtifactCoords, List<MemberReport>> rootsToMembers = tracePattern == null ? Map.of() : new HashMap<>();
        final Map<ArtifactCoords, ArtifactCoords> allNodes = redhatVersionRate ? new ConcurrentHashMap<>() : null;
        final AtomicInteger inspectedRoots = new AtomicInteger();
        final AtomicInteger redhatVersions = new AtomicInteger();
        var treeVisitor = initTreeVisitor(tracePattern, allNodes, redhatVersions, inspectedRoots, memberReports, coreReport,
                rootsToMembers);
        var treeInspector = initTreeInspector(resolver, treeVisitor);
        var coreConstraints = readBomConstraints(platform.getCore().getBom(), resolver);

        for (var m : memberReports) {
            final List<Dependency> effectiveConstraints;
            if (m.metadata == platform.getCore()) {
                m.bomConstraints = mapConstraints(coreConstraints);
                effectiveConstraints = coreConstraints;
                if (m.enabled) {
                    var pluginCoords = platform.getMavenPlugin();
                    if (isVersionSelected(pluginCoords.getVersion())) {
                        treeInspector.inspectPlugin(getAetherArtifact(pluginCoords));
                        if (tracePattern != null) {
                            rootsToMembers.computeIfAbsent(pluginCoords, k -> new ArrayList<>(1)).add(m);
                        }
                    }
                    for (var extraKey : EXTRA_CORE_ARTIFACTS) {
                        var extraArtifact = ArtifactCoords.of(extraKey.getGroupId(), extraKey.getArtifactId(),
                                extraKey.getClassifier(), extraKey.getType(), platform.getCore().getQuarkusCoreVersion());
                        var d = m.bomConstraints.get(extraArtifact);
                        if (d == null && RhVersionPattern.isRhVersion(platform.getCore().getQuarkusCoreVersion())) {
                            extraArtifact = ArtifactCoords.of(extraKey.getGroupId(), extraKey.getArtifactId(),
                                    extraKey.getClassifier(), extraKey.getType(),
                                    RhVersionPattern.ensureNoRhQualifier(platform.getCore().getQuarkusCoreVersion()));
                            d = m.bomConstraints.get(extraArtifact);
                        }
                        if (d == null) {
                            log.warn("Failed to locate " + extraArtifact + " among "
                                    + platform.getCore().getBom().toCompactCoords() + " constraints");
                        } else if (isVersionSelected(d.getArtifact().getVersion())) {
                            if (tracePattern != null) {
                                rootsToMembers.computeIfAbsent(extraArtifact, k -> new ArrayList<>(1)).add(m);
                            }
                            treeInspector.inspectAsDependency(d.getArtifact(), effectiveConstraints, d.getExclusions());
                        }
                    }
                }
            } else {
                var tmp = readBomConstraints(m.metadata.getBom(), resolver);
                effectiveConstraints = new ArrayList<>(coreConstraints.size() + tmp.size());
                effectiveConstraints.addAll(coreConstraints);
                effectiveConstraints.addAll(tmp);
                m.bomConstraints = mapConstraints(tmp);
            }
            if (m.enabled) {
                for (var e : m.metadata.getExtensions()) {
                    if (isVersionSelected(e.getVersion())) {
                        var d = m.bomConstraints.get(e);
                        treeInspector.inspectAsDependency(getAetherArtifact(e), effectiveConstraints,
                                d == null ? List.of() : d.getExclusions());
                        if (tracePattern != null) {
                            rootsToMembers.computeIfAbsent(e, k -> new ArrayList<>(1)).add(m);
                        }

                        if (!runtimeOnly) {
                            var deployment = ArtifactCoords.of(e.getGroupId(), e.getArtifactId() + "-deployment",
                                    e.getClassifier(),
                                    e.getType(), e.getVersion());
                            d = m.bomConstraints.get(deployment);
                            if (d == null) {
                                var jar = resolver.resolve(getAetherArtifact(e)).getArtifact().getFile().toPath();
                                deployment = PathTree.ofDirectoryOrArchive(jar).apply(BootstrapConstants.DESCRIPTOR_PATH,
                                        visit -> {
                                            var props = new Properties();
                                            try (BufferedReader reader = Files.newBufferedReader(visit.getPath())) {
                                                props.load(reader);
                                            } catch (IOException ioe) {
                                                throw new UncheckedIOException(ioe);
                                            }
                                            var coords = props.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
                                            if (coords == null) {
                                                throw new RuntimeException(
                                                        visit.getUrl() + " is missing property "
                                                                + BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
                                            }
                                            return ArtifactCoords.fromString(coords);
                                        });
                            }
                            d = m.bomConstraints.get(deployment);
                            treeInspector.inspectAsDependency(getAetherArtifact(deployment), effectiveConstraints,
                                    d == null ? List.of() : d.getExclusions());
                            if (tracePattern != null) {
                                rootsToMembers.computeIfAbsent(deployment, k -> new ArrayList<>(1)).add(m);
                            }
                        }
                    }
                }
            }

            if (tracePattern != null) {
                for (var d : m.bomConstraints.values()) {
                    var a = d.getArtifact();
                    if (tracePattern.contains(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(),
                            a.getVersion())) {
                        m.addTracedBomConstraint(d);
                    }
                }
            }
        }

        treeInspector.complete();

        logMemberReports(memberReports);

        if (allNodes != null) {
            log.info(String.format("%-32s: %s", "Number of root artifacts", inspectedRoots));
            log.info(String.format("%-32s: %s", "Total number of artifacts", allNodes.size()));
            if (!allNodes.isEmpty()) {
                log.info(String.format("%-32s: %s (%.1f%%)", "Artifacts with Red Hat versions",
                        redhatVersions, ((double) redhatVersions.get() * 100) / allNodes.size()));

                if (logNotMatched) {
                    log.info("");
                    log.info("Non Red Hat version artifacts:");
                    allNodes.keySet()
                            .stream().sorted(Comparator.comparing(Object::toString))
                            .filter(coords -> !RhVersionPattern.isRhVersion(coords.getVersion()))
                            .forEach(coords -> log.info(String.format(" * %s", coords)));
                }
            }
            log.info("");
        }

        return 0;
    }

    private DependencyTreeVisitor<TreeNode> initTreeVisitor(ArtifactSet tracePattern,
            Map<ArtifactCoords, ArtifactCoords> allNodes,
            AtomicInteger redhatVersionsTotal, AtomicInteger inspectedRoots,
            ArrayList<MemberReport> memberReports, MemberReport coreReport,
            Map<ArtifactCoords, List<MemberReport>> rootsToMembers) {
        return new DependencyTreeVisitor<>() {

            final Map<Artifact, String> enforcedBy = new HashMap<>();

            @Override
            public void visit(DependencyTreeVisit<TreeNode> visit) {
                if (tracePattern != null || redhatVersionRate) {
                    inspectedRoots.incrementAndGet();
                    var result = visit(visit, visit.getRoot());
                    if (result != null) {
                        visit.pushEvent(result);
                    }
                }
            }

            private TreeNode visit(DependencyTreeVisit<TreeNode> visit, DependencyNode node) {
                var a = node.getArtifact();
                if (allNodes != null) {
                    var coords = ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(),
                            a.getVersion());
                    if (allNodes.put(coords, coords) == null && RhVersionPattern.isRhVersion(a.getVersion())) {
                        redhatVersionsTotal.incrementAndGet();
                    }
                }
                TreeNode result = null;
                if (tracePattern != null
                        && tracePattern.contains(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(),
                                a.getVersion())) {
                    result = new TreeNode(a, true, getEnforcedInfo(a));
                }
                for (var child : node.getChildren()) {
                    var childResult = visit(visit, child);
                    if (childResult != null) {
                        if (result == null) {
                            result = new TreeNode(a, false);
                        }
                        result.addChild(childResult);
                    }
                }
                return result;
            }

            private String getEnforcedInfo(Artifact a) {
                return enforcedBy.computeIfAbsent(a, k -> {
                    var coords = ArtifactCoords.of(k.getGroupId(), k.getArtifactId(), k.getClassifier(),
                            k.getExtension(), k.getVersion());
                    StringBuilder sb = null;
                    for (var report : memberReports) {
                        if ((report.enabled || report == coreReport) && report.bomConstraints.containsKey(coords)) {
                            if (sb == null) {
                                sb = new StringBuilder().append(" [managed by ");
                            } else {
                                sb.append(", ");
                            }
                            sb.append(report.metadata.getBom().getArtifactId());
                        }
                    }
                    return sb == null ? "" : sb.append("]").toString();
                });
            }

            @Override
            public void onEvent(TreeNode root, MessageWriter log) {
                if (!rootsToMembers.isEmpty()) {
                    var a = root.artifact;
                    var reports = rootsToMembers.get(ArtifactCoords.of(a.getGroupId(), a.getArtifactId(),
                            a.getClassifier(), a.getExtension(), a.getVersion()));
                    if (reports != null) {
                        for (var report : reports) {
                            report.addTracedExtensionDependency(root);
                        }
                    }
                }
            }

            @Override
            public void handleResolutionFailures(Collection<DependencyTreeError> requests) {
            }
        };
    }

    private void logMemberReports(ArrayList<MemberReport> memberReports) {
        int membersWithTraces = 0;
        for (var report : memberReports) {
            if (report.enabled && report.hasTraces()) {
                ++membersWithTraces;
                log.info("");
                log.info(MEMBER_HEADER_PREFIX + report.metadata.getBom().getGroupId().toUpperCase()
                        + ":" + report.metadata.getBom().getArtifactId().toUpperCase()
                        + ":" + report.metadata.getBom().getVersion().toUpperCase());
                if (!report.tracedBomConstraints.isEmpty()) {
                    log.info("");
                    log.info(BOM_ENTRY_HEADER_PREFIX + "BOM entries");
                    log.info("");
                    for (var v : report.tracedBomConstraints) {
                        log.info(toCompactCoords(v.getArtifact()));
                    }
                }
                if (!report.tracedExtensionDeps.isEmpty()) {
                    log.info("");
                    log.info(EXTENSIONS_HEADER_PREFIX + "Extension dependencies");
                    for (var result : report.tracedExtensionDeps) {
                        log.info("");
                        result.log(log, tree);
                    }
                }
            }
        }
        log.info("");

        if (trace != null && !trace.isEmpty() && membersWithTraces == 0) {
            var sb = new StringBuilder()
                    .append("No traces of ");
            var i = trace.iterator();
            sb.append(i.next());
            if (i.hasNext()) {
                var next = i.next();
                while (i.hasNext()) {
                    sb.append(", ").append(next);
                    next = i.next();
                }
                sb.append(" and ").append(next);
            }
            log.info(sb.append(" found").toString());
        }
    }

    private DependencyTreeInspector initTreeInspector(MavenArtifactResolver resolver,
            DependencyTreeVisitor<TreeNode> treeVisitor) {
        return DependencyTreeInspector.configure()
                .setArtifactResolver(resolver)
                .setResolveDependencies(resolve)
                .setParallelProcessing(parallelProcessing)
                .setProgressTrackerPrefix("Inspecting ")
                .setTreeVisitor(treeVisitor);
    }

    private ArtifactSet initTracePattern() {
        final ArtifactSet tracePattern;
        if (trace != null && !trace.isEmpty()) {
            var builder = ArtifactSet.builder();
            for (var exp : trace) {
                builder.include(toArtifactCoordsPattern(exp));
            }
            tracePattern = builder.build();
        } else {
            tracePattern = null;
        }
        return tracePattern;
    }

    private QuarkusPlatformInfo readPlatformInfo(MavenArtifactResolver resolver) {
        return QuarkusPlatformInfoReader.builder()
                .setResolver(resolver)
                .setVersion(version)
                .setPlatformKey(platformGroupId)
                .build()
                .readPlatformInfo();
    }

    private boolean isMemberSelected(QuarkusPlatformInfo.Member member) {
        return members.isEmpty() || members.contains(member.getBom().getArtifactId());
    }

    private boolean isVersionSelected(String version) {
        if (extensionVersions.isEmpty()) {
            return true;
        }
        if (extensionVersionPatterns == null) {
            var patterns = new ArrayList<Pattern>(extensionVersions.size());
            for (var vp : extensionVersions) {
                patterns.add(Pattern.compile(GlobUtil.toRegexPattern(vp)));
            }
            extensionVersionPatterns = patterns;
        }
        for (var p : extensionVersionPatterns) {
            if (p.matcher(version).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Map<ArtifactCoords, Dependency> mapConstraints(List<Dependency> deps) {
        var map = new HashMap<ArtifactCoords, Dependency>(deps.size());
        for (var d : deps) {
            var a = d.getArtifact();
            map.put(ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion()),
                    d);
        }
        return map;
    }

    private static List<Dependency> readBomConstraints(ArtifactCoords bom, MavenArtifactResolver resolver) {
        try {
            return resolver.resolveDescriptor(getAetherArtifact(bom)).getManagedDependencies();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to resolve artifact descriptor of " + bom, e);
        }
    }

    private static final String MRRC_URL = "https://maven.repository.redhat.com/ga";

    private MavenArtifactResolver getResolver() throws BootstrapMavenException {
        var config = BootstrapMavenContext.config()
                .setWorkspaceDiscovery(false)
                .setArtifactTransferLogging(false);
        if (settings != null) {
            var f = new File(settings);
            if (!f.exists()) {
                throw new IllegalArgumentException(f + " does not exist");
            }
            config.setUserSettings(f);
        }
        if (repoDir != null) {
            config.setLocalRepository(repoDir);
        }
        if (mavenProfiles != null) {
            System.setProperty(BootstrapMavenOptions.QUARKUS_INTERNAL_MAVEN_CMD_LINE_ARGS, "-P" + mavenProfiles);
        }
        var mvnCtx = new BootstrapMavenContext(config);
        // if the version is a redhat one, enable the redhat repository in case it's not configured
        if (version != null && RhVersionPattern.isRhVersion(version)) {
            boolean redhatConfigured = false;
            for (var r : mvnCtx.getRemoteRepositories()) {
                if (redhatConfigured = isRedhat(r)) {
                    break;
                }
            }
            if (!redhatConfigured) {
                var mrrc = new RemoteRepository.Builder("redhat", "default", MRRC_URL).build();
                mvnCtx = new BootstrapMavenContext(
                        BootstrapMavenContext.config()
                                .setRemoteRepositoryManager(mvnCtx.getRemoteRepositoryManager())
                                .setRepositorySystem(mvnCtx.getRepositorySystem())
                                .setRepositorySystemSession(mvnCtx.getRepositorySystemSession())
                                .setRemoteRepositories(
                                        mvnCtx.getRemoteRepositoryManager()
                                                .aggregateRepositories(mvnCtx.getRepositorySystemSession(),
                                                        List.of(mrrc),
                                                        mvnCtx.getRemoteRepositories(), false))
                                .setRemotePluginRepositories(
                                        mvnCtx.getRemoteRepositoryManager()
                                                .aggregateRepositories(mvnCtx.getRepositorySystemSession(),
                                                        List.of(mrrc),
                                                        mvnCtx.getRemotePluginRepositories(), false)));
            }
        }
        return new MavenArtifactResolver(mvnCtx);
    }

    private static boolean isRedhat(RemoteRepository repo) {
        // it could be MRRC or another RH repo
        if (repo.getUrl().contains("redhat.com")) {
            return true;
        }
        for (var mirrored : repo.getMirroredRepositories()) {
            if (isRedhat(mirrored)) {
                return true;
            }
        }
        return false;
    }

    private static String toCompactCoords(Artifact a) {
        var sb = new StringBuilder();
        sb.append(a.getGroupId()).append(":").append(a.getArtifactId()).append(":");
        if (!a.getClassifier().isEmpty()) {
            sb.append(a.getClassifier()).append(":");
        }
        if (!ArtifactCoords.TYPE_JAR.equals(a.getExtension())) {
            sb.append(a.getExtension()).append(":");
        }
        return sb.append(a.getVersion()).toString();
    }

    private static Artifact getAetherArtifact(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                coords.getType(), coords.getVersion());
    }

    public static ArtifactCoordsPattern toArtifactCoordsPattern(String wildcardPattern) {
        final String wildcard = "*";
        var groupIdPattern = wildcard;
        var artifactIdPattern = wildcard;
        var typePattern = wildcard;
        var classifierPattern = wildcard;
        var versionPattern = wildcard;
        var parts = wildcardPattern.split(":");
        if (parts.length > 0) {
            if (parts.length == 1 && wildcardPattern.charAt(wildcardPattern.length() - 1) == ':') {
                groupIdPattern = parts[0];
            } else {
                artifactIdPattern = parts[0];
            }
            if (parts.length > 1) {
                groupIdPattern = parts[0];
                artifactIdPattern = parts[1];
                if (parts.length > 2) {
                    final String third = parts[2];
                    if (parts.length > 3) {
                        final String fourth = parts[3];
                        if (parts.length > 4) {
                            classifierPattern = third;
                            typePattern = fourth;
                            versionPattern = parts[4];
                        } else {
                            throw new IllegalStateException(
                                    "Expected groupId:artifactId:version or groupId:artifactId:classifier:type:version; found: "
                                            + wildcardPattern);
                        }
                    } else {
                        versionPattern = third;
                    }
                }
            }
        }
        return ArtifactCoordsPattern.builder()
                .setGroupId(groupIdPattern)
                .setArtifactId(artifactIdPattern)
                .setClassifier(classifierPattern)
                .setType(typePattern)
                .setVersion(versionPattern)
                .build();
    }

    private static class MemberReport {
        private final QuarkusPlatformInfo.Member metadata;
        private final boolean enabled;
        private Map<ArtifactCoords, Dependency> bomConstraints = Map.of();
        private List<Dependency> tracedBomConstraints = List.of();
        private List<TreeNode> tracedExtensionDeps = List.of();

        MemberReport(QuarkusPlatformInfo.Member metadata) {
            this(metadata, true);
        }

        MemberReport(QuarkusPlatformInfo.Member metadata, boolean enabled) {
            this.metadata = metadata;
            this.enabled = enabled;
        }

        void addTracedBomConstraint(Dependency d) {
            if (tracedBomConstraints.isEmpty()) {
                tracedBomConstraints = new ArrayList<>();
            }
            tracedBomConstraints.add(d);
        }

        void addTracedExtensionDependency(TreeNode root) {
            if (tracedExtensionDeps.isEmpty()) {
                tracedExtensionDeps = new ArrayList<>();
            }
            tracedExtensionDeps.add(root);
        }

        boolean hasTraces() {
            return !tracedExtensionDeps.isEmpty() || !tracedBomConstraints.isEmpty();
        }
    }

    private static class TreeNode {

        private static final String ARROW = "↳";
        private static final String VERTICAL_LINE = "│  ";
        private static final String MIDDLE_LINK = "├─ ";
        private static final String LAST_LINK = "└─ ";

        final Artifact artifact;
        final String enforcedBy;
        final boolean matched;
        List<TreeNode> tracedChildren = List.of();

        private TreeNode(Artifact a, boolean matched) {
            this(a, matched, null);
        }

        private TreeNode(Artifact a, boolean matched, String enforcedBy) {
            this.artifact = a;
            this.enforcedBy = enforcedBy;
            this.matched = matched;
        }

        private void addChild(TreeNode child) {
            if (tracedChildren.isEmpty()) {
                tracedChildren = new ArrayList<>(2);
            }
            tracedChildren.add(child);
        }

        private void log(MessageWriter log, boolean fullChain) {
            if (fullChain) {
                log(new ArrayList<>(), log);
            } else {
                var queue = new ArrayList<>(tracedChildren);
                var result = new ArrayList<TreeNode>();
                for (int i = 0; i < queue.size(); ++i) {
                    var child = queue.get(i);
                    if (child.matched) {
                        result.add(child);
                    }
                    queue.addAll(child.tracedChildren);
                }

                var sb = new StringBuilder();
                append(sb);
                log.info(sb.toString());

                for (var child : result) {
                    sb = new StringBuilder();
                    sb.append(ARROW).append(' ');
                    child.append(sb);
                    log.info(sb.toString());
                }
            }
        }

        private void log(List<Boolean> depth, MessageWriter log) {
            var sb = new StringBuilder();
            if (!depth.isEmpty()) {
                for (int i = 0; i < depth.size() - 1; ++i) {
                    if (depth.get(i)) {
                        sb.append(VERTICAL_LINE);
                    } else {
                        sb.append("   ");
                    }
                }
                if (depth.get(depth.size() - 1)) {
                    sb.append(MIDDLE_LINK);
                } else {
                    sb.append(LAST_LINK);
                }
            }
            append(sb);
            log.info(sb.toString());

            final int childrenTotal = tracedChildren.size();
            if (childrenTotal > 0) {
                if (childrenTotal == 1) {
                    depth.add(false);
                    tracedChildren.get(0).log(depth, log);
                } else {
                    depth.add(true);
                    int i = 0;
                    while (i < childrenTotal) {
                        tracedChildren.get(i++).log(depth, log);
                        if (i == childrenTotal - 1) {
                            depth.set(depth.size() - 1, false);
                        }
                    }
                }
                depth.remove(depth.size() - 1);
            }
        }

        private void append(StringBuilder out) {
            out.append(artifact.getGroupId()).append(':').append(artifact.getArtifactId()).append(':');
            if (!artifact.getClassifier().isEmpty()) {
                out.append(artifact.getClassifier()).append(':');
            }
            if (!ArtifactCoords.TYPE_JAR.equals(artifact.getExtension())) {
                if (artifact.getClassifier().isEmpty()) {
                    out.append(':');
                }
                out.append(artifact.getExtension()).append(':');
            }
            out.append(artifact.getVersion());
            if (enforcedBy != null) {
                out.append(enforcedBy);
            }
        }
    }
}
