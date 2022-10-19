package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.decomposer.maven.GenerateMavenRepoZip;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContextConfig;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.util.GlobUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class MavenRepoZip {

    private static final String JAVADOC = "javadoc";
    private static final String SOURCES = "sources";

    public class Generator {

        private static final String ORIGINAL_LOCAL_REPO = "original-local";
        private static final String MAVEN_REPO_ZIP = "maven-repo-zip";
        private static final String REPOSITORY = "repository";

        private Generator() {
        }

        public Generator setConfig(GenerateMavenRepoZip config) {
            repoDir = Path.of(config == null ? REPOSITORY
                    : config.getRepositoryDir() == null ? REPOSITORY : config.getRepositoryDir()).normalize()
                    .toAbsolutePath();
            excludedGroupIds = config.getExcludedGroupIds();
            if (!config.getExcludedArtifacts().isEmpty()) {
                excludedArtifacts = config.getExcludedArtifacts().stream().map(ArtifactKey::fromString)
                        .collect(Collectors.toSet());
            }
            if (!config.getExtraArtifacts().isEmpty()) {
                extraArtifacts = config.getExtraArtifacts().stream().map(ArtifactCoords::fromString)
                        .collect(Collectors.toList());
            }
            if (config.getIncludedVersionsPattern() != null) {
                includedVersionsPattern = Pattern.compile(GlobUtil.toRegexPattern(config.getIncludedVersionsPattern()));
            }
            return this;
        }

        public Generator setLog(MessageWriter logger) {
            log = logger;
            return this;
        }

        public Generator setMavenArtifactResolver(MavenArtifactResolver mavenResolver) {
            resolver = mavenResolver;
            return this;
        }

        public Generator setManagedDependencies(List<Dependency> managedDependencies) {
            managedDeps = managedDependencies;
            return this;
        }

        public void generate() {
            if (repoDir == null) {
                repoDir = Path.of(REPOSITORY);
            }
            repoDir = repoDir.toAbsolutePath().normalize();

            if (log == null) {
                log = MessageWriter.info();
            }

            final BootstrapMavenContext mavenContext;
            try {
                mavenContext = new BootstrapMavenContext(
                        BootstrapMavenContext.config().setWorkspaceDiscovery(false));
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize Maven context", e);
            }
            final Settings settings = getBaseMavenSettings(mavenContext.getUserSettings());
            settings.setLocalRepository(repoDir.toString());

            final Profile profile = new Profile();
            profile.setId(MAVEN_REPO_ZIP);
            settings.addActiveProfile(MAVEN_REPO_ZIP);
            settings.addProfile(profile);

            Repository repo;
            try {
                repo = configureRepo(ORIGINAL_LOCAL_REPO,
                        Path.of(mavenContext.getLocalRepo()).toUri().toURL().toExternalForm());
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure repository", e);
            }
            profile.addRepository(repo);
            profile.addPluginRepository(repo);

            final Path settingsXml = repoDir.resolve("settings.xml");
            try {
                Files.createDirectories(repoDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory " + repoDir, e);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(settingsXml)) {
                new DefaultSettingsWriter().write(writer, Map.of(), settings);
            } catch (IOException e) {
                throw new RuntimeException("Failed to persist Maven settings to " + settingsXml, e);
            }

            final BootstrapMavenContextConfig<?> resolverConfig = BootstrapMavenContext.config();
            if (resolver != null) {
                resolverConfig.setRepositorySystem(resolver.getSystem());
                resolverConfig.setRemoteRepositoryManager(resolver.getRemoteRepositoryManager());
                resolverConfig.setCurrentProject(resolver.getMavenContext().getCurrentProject());
            }
            try {
                resolver = new MavenArtifactResolver(
                        new BootstrapMavenContext(resolverConfig.setUserSettings(settingsXml.toFile())));
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
            }

            // prefer the original-local over the others
            List<RemoteRepository> finalRepos = new ArrayList<>(resolver.getRepositories());
            final Iterator<RemoteRepository> ir = finalRepos.iterator();
            RemoteRepository originalLocalRepo = null;
            while (ir.hasNext()) {
                final RemoteRepository r = ir.next();
                if (r.getId().equals(ORIGINAL_LOCAL_REPO)) {
                    originalLocalRepo = r;
                    ir.remove();
                    break;
                }
            }
            if (originalLocalRepo != null) {
                final List<RemoteRepository> tmp = new ArrayList<>(finalRepos.size() + 1);
                tmp.add(originalLocalRepo);
                tmp.addAll(finalRepos);
                finalRepos = tmp;
                try {
                    resolver = MavenArtifactResolver.builder()
                            .setRemoteRepositoryManager(resolver.getRemoteRepositoryManager())
                            .setRepositorySystem(resolver.getSystem())
                            .setRepositorySystemSession(resolver.getSession())
                            .setRemoteRepositories(finalRepos)
                            .setCurrentProject(resolver.getMavenContext().getCurrentProject())
                            .build();
                } catch (BootstrapMavenException e) {
                    throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
                }
            }

            MavenRepoZip.this.doGenerate();
        }

        private Repository configureRepo(String id, String url) {
            final Repository repo = new Repository();
            repo.setId(id);
            repo.setLayout("default");
            repo.setUrl(url);
            RepositoryPolicy policy = new RepositoryPolicy();
            policy.setEnabled(true);
            repo.setReleases(policy);
            repo.setSnapshots(policy);
            return repo;
        }

        private Settings getBaseMavenSettings(File mavenSettings) {
            if (mavenSettings != null && mavenSettings.exists()) {
                try {
                    return new DefaultSettingsReader().read(mavenSettings, Map.of());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read Maven settings from " + mavenSettings, e);
                }
            }
            return new Settings();
        }

    }

    public static Generator newGenerator() {
        return new MavenRepoZip().new Generator();
    }

    private Path repoDir;
    private MavenArtifactResolver resolver;
    private List<Dependency> managedDeps = List.of();
    private MessageWriter log;
    private Set<String> excludedGroupIds = Set.of();
    private Set<ArtifactKey> excludedArtifacts = Set.of();
    private List<ArtifactCoords> extraArtifacts = List.of();
    private Pattern includedVersionsPattern;

    private void doGenerate() {
        log.info("Generating Maven repository at " + repoDir);
        //IoUtils.recursiveDelete(repoDir);

        for (Dependency d : managedDeps) {
            collectDependencies(d.getArtifact());
        }
        for (ArtifactCoords coords : extraArtifacts) {
            collectDependencies(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                    coords.getType(), coords.getVersion()));
        }
    }

    private void collectDependencies(Artifact artifact) {
        if (isFilteredOut(artifact)) {
            return;
        }
        try {
            resolver.resolveDependencies(artifact, managedDeps);
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to collect dependencies of " + artifact, e);
        }

        if (artifact.getExtension().equals(ArtifactCoords.TYPE_POM)) {
            return;
        }
        if (ArtifactCoords.TYPE_JAR.equals(artifact.getExtension())) {
            // sources
            resolveOrNull(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), SOURCES,
                    ArtifactCoords.TYPE_JAR, artifact.getVersion()));
            // javadoc
            resolveOrNull(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), JAVADOC,
                    ArtifactCoords.TYPE_JAR, artifact.getVersion()));
        }
    }

    private boolean isFilteredOut(final Artifact artifact) {
        return artifact == null
                //|| node.getDependency() != null && node.getDependency().isOptional()
                || JAVADOC.equals(artifact.getClassifier())
                || SOURCES.equals(artifact.getClassifier())
                || includedVersionsPattern != null && !includedVersionsPattern.matcher(artifact.getVersion()).matches()
                || excludedGroupIds.contains(artifact.getGroupId())
                || excludedArtifacts.contains(getKey(artifact));
    }

    private Artifact resolveOrNull(Artifact rtArtifact) {
        try {
            return resolver.resolve(rtArtifact).getArtifact();
        } catch (BootstrapMavenException e) {
            return null;
        }
    }

    private static ArtifactKey getKey(Artifact a) {
        return ArtifactKey.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension());
    }

    public static void main(String[] args) throws Exception {

        final Set<String> prodSet = collectPaths(Paths.get(System.getProperty("user.home"))
                .resolve("Downloads/rh-quarkus-2.2.3.GA-maven-repository/maven-repository"));
        final Set<String> platformSet = collectPaths(
                Paths.get(System.getProperty("user.home")).resolve("git/quarkus-platform-product/target/repository"));

        final Set<String> missing = new HashSet<>();
        final Set<String> extra = new HashSet<>();

        for (String s : prodSet) {
            if (!platformSet.contains(s)) {
                missing.add(s);
            }
        }
        for (String s : platformSet) {
            if (!prodSet.contains(s)) {
                extra.add(s);
            }
        }

        if (!missing.isEmpty()) {
            System.out.println("MISSING ARTIFACTS " + missing.size());
            logPaths(missing);
        }
        if (!extra.isEmpty()) {
            System.out.println("EXTRA ARTIFACTS " + extra.size());
            logPaths(extra);
        }
    }

    private static void logPaths(final Set<String> prodSet) {
        ArrayList<String> list = new ArrayList<>(prodSet);
        Collections.sort(list);
        list.forEach(s -> System.out.println(s));
    }

    private static Set<String> collectPaths(Path root) throws IOException {
        final Set<String> paths = new HashSet<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                final String name = file.getFileName().toString();
                if (!name.endsWith(".md5") && !name.endsWith(".sha1")) {
                    paths.add(root.relativize(file).toString());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return paths;
    }
}
