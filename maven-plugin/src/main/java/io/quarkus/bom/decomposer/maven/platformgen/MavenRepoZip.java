package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bom.decomposer.maven.GenerateMavenRepoZip;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.util.GlobUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

public class MavenRepoZip {

    private static final String JAVADOC = "javadoc";
    private static final String SOURCES = "sources";

    public class Generator {

        private static final String REPOSITORY = "repository";

        private Generator() {
        }

        public Generator setConfig(GenerateMavenRepoZip config) {
            repoDir = Paths.get(config == null ? REPOSITORY
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

        public void generate() throws MojoExecutionException {
            if (repoDir == null) {
                repoDir = Path.of(REPOSITORY);
            }
            if (log == null) {
                log = MessageWriter.info();
            }
            if (resolver != null) {
                final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(resolver.getSession());
                final LocalRepositoryManager original = resolver.getSession().getLocalRepositoryManager();
                session.setLocalRepositoryManager(new LocalRepositoryManager() {

                    @Override
                    public LocalRepository getRepository() {
                        return original.getRepository();
                    }

                    @Override
                    public String getPathForLocalArtifact(Artifact artifact) {
                        return original.getPathForLocalArtifact(artifact);
                    }

                    @Override
                    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository,
                            String context) {
                        return original.getPathForRemoteArtifact(artifact, repository, context);
                    }

                    @Override
                    public String getPathForLocalMetadata(Metadata metadata) {
                        return original.getPathForLocalMetadata(metadata);
                    }

                    @Override
                    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository,
                            String context) {
                        return original.getPathForRemoteMetadata(metadata, repository, context);
                    }

                    @Override
                    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
                        final LocalArtifactResult result = original.find(session, request);
                        if (result.isAvailable() && !isFilteredOut(request.getArtifact())) {
                            try {
                                copyArtifact(request.getArtifact());
                            } catch (MojoExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return result;
                    }

                    @Override
                    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
                        original.add(session, request);
                        if (!isFilteredOut(request.getArtifact())) {
                            try {
                                copyArtifact(request.getArtifact());
                            } catch (MojoExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    @Override
                    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
                        return original.find(session, request);
                    }

                    @Override
                    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
                        original.add(session, request);
                    }
                });
                try {
                    resolver = new MavenArtifactResolver(new BootstrapMavenContext(BootstrapMavenContext.config()
                            .setRepositorySystem(resolver.getSystem())
                            .setRepositorySystemSession(session)
                            .setRemoteRepositoryManager(resolver.getRemoteRepositoryManager())
                            .setRemoteRepositories(resolver.getRepositories())
                            .setCurrentProject(resolver.getMavenContext().getCurrentProject())));
                } catch (BootstrapMavenException e) {
                    throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
                }
            }
            MavenRepoZip.this.doGenerate();
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
    private final Set<ArtifactCoords> copiedArtifacts = new HashSet<>();

    private void doGenerate() throws MojoExecutionException {
        log.info("Generating Maven repository at " + repoDir);
        IoUtils.recursiveDelete(repoDir);

        for (Dependency d : managedDeps) {
            collectDependencies(d.getArtifact());
        }
        for (ArtifactCoords coords : extraArtifacts) {
            collectDependencies(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                    coords.getType(), coords.getVersion()));
        }
    }

    private void collectDependencies(Artifact artifact)
            throws MojoExecutionException {
        if (isFilteredOut(artifact)) {
            return;
        }
        final DependencyNode root;
        try {
            root = resolver.collectManagedDependencies(artifact, List.of(), managedDeps,
                    List.of(), List.of(), JavaScopes.TEST, JavaScopes.PROVIDED).getRoot();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to collect dependencies of " + artifact, e);
        }
        copyDependencies(root);
    }

    private void copyDependencies(DependencyNode node) throws MojoExecutionException {
        for (DependencyNode child : node.getChildren()) {
            if (child.getDependency() != null && child.getDependency().isOptional()) {
                continue;
            }
            copyDependencies(child);
        }
        final Artifact artifact = node.getArtifact();
        if (isFilteredOut(artifact)) {
            return;
        }
        copyArtifact(artifact);
        if (artifact.getExtension().equals(ArtifactCoords.TYPE_POM)) {
            return;
        }

        if (ArtifactCoords.TYPE_JAR.equals(artifact.getExtension())) {
            // sources
            Artifact a = resolveOrNull(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), SOURCES,
                    ArtifactCoords.TYPE_JAR, artifact.getVersion()));
            if (a != null) {
                copyArtifact(a);
            }
            // javadoc
            a = resolveOrNull(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), JAVADOC,
                    ArtifactCoords.TYPE_JAR, artifact.getVersion()));
            if (a != null) {
                copyArtifact(a);
            }
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

    private void copyArtifact(final Artifact artifact) throws MojoExecutionException {
        if (!copiedArtifacts.add(toCoords(artifact))) {
            return;
        }
        File resolved = artifact.getFile();
        if (resolved == null) {
            resolved = resolve(artifact).getFile();
        }
        final Path target = repoDir
                .resolve(resolver.getSession().getLocalRepositoryManager().getPathForLocalArtifact(artifact));
        copyFile(resolved.toPath(), target);
    }

    private Artifact resolve(Artifact rtArtifact) throws MojoExecutionException {
        try {
            return resolver.resolve(rtArtifact).getArtifact();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to resolve " + rtArtifact, e);
        }
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

    private static ArtifactCoords toCoords(Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    public static void copyFile(Path source, Path target) throws MojoExecutionException {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories " + target.getParent(), e);
        }
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy " + source + " to " + target, e);
        }
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
