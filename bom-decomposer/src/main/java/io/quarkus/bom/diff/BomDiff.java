package io.quarkus.bom.diff;

import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class BomDiff {

    public static Config config() {
        return new Config();
    }

    public static class Config {

        private Artifact mainBom;
        private String mainSource;
        private URL mainUrl;
        private Artifact toBom;
        private String toSource;
        private URL toUrl;
        private List<Dependency> mainDeps;
        private List<Dependency> toDeps;

        private ArtifactResolver resolver;

        private Config() {
        }

        public Config resolver(ArtifactResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        public Config compare(Artifact bomArtifact) {
            final ArtifactResolver resolver = defaultResolver();
            final ArtifactDescriptorResult descr = resolver.describe(bomArtifact);
            mainBom = descr.getArtifact();
            mainSource = bomArtifact.toString();
            mainUrl = toUrl(resolver.resolve(bomArtifact).getArtifact().getFile().toPath());
            mainDeps = descr.getManagedDependencies();
            return this;
        }

        public Config compare(Path bomPath) {
            final ArtifactDescriptorResult descr = descriptor(bomPath);
            mainBom = descr.getArtifact();
            mainSource = bomPath.toString();
            mainUrl = toUrl(bomPath);
            mainDeps = descr.getManagedDependencies();
            return this;
        }

        public BomDiff to(String groupId, String artifactId, String version) {
            return to(new DefaultArtifact(groupId, artifactId, null, "pom", version));
        }

        public BomDiff to(Artifact bomArtifact) {
            final ArtifactResolver resolver = defaultResolver();
            final ArtifactDescriptorResult descr = resolver.describe(bomArtifact);
            toBom = descr.getArtifact();
            toSource = bomArtifact.toString();
            toUrl = toUrl(resolver.resolve(bomArtifact).getArtifact().getFile().toPath());
            toDeps = descr.getManagedDependencies();
            return diff();
        }

        public BomDiff to(Path bomPath) {
            final ArtifactDescriptorResult descr = descriptor(bomPath);
            toBom = descr.getArtifact();
            toSource = bomPath.toString();
            toUrl = toUrl(bomPath);
            toDeps = descr.getManagedDependencies();
            return diff();
        }

        private ArtifactDescriptorResult descriptor(Path pom) {

            final MavenArtifactResolver.Builder resolverBuilder = MavenArtifactResolver.builder()
                    .setCurrentProject(pom.normalize().toAbsolutePath().toString());
            if (resolver != null) {
                final MavenArtifactResolver baseResolver = resolver.underlyingResolver();
                resolverBuilder.setRepositorySystem(baseResolver.getSystem())
                        .setRemoteRepositoryManager(baseResolver.getRemoteRepositoryManager())
                        .setSettingsDecrypter(baseResolver.getMavenContext().getSettingsDecrypter());
            }
            MavenArtifactResolver underlyingResolver;
            try {
                underlyingResolver = resolverBuilder.build();
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize Maven artifact resolver for " + pom, e);
            }

            final BootstrapMavenContext mvnCtx = underlyingResolver.getMavenContext();
            final LocalProject bomProject = mvnCtx.getCurrentProject();
            Artifact pomArtifact = new DefaultArtifact(bomProject.getGroupId(), bomProject.getArtifactId(),
                    ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM, bomProject.getVersion());
            pomArtifact = pomArtifact.setFile(pom.toFile());

            return ArtifactResolverProvider.get(underlyingResolver, resolver.getBaseDir()).describe(pomArtifact);
        }

        private ArtifactResolver defaultResolver() {
            return resolver == null ? resolver = ArtifactResolverProvider.get() : resolver;
        }

        private URL toUrl(Path p) {
            try {
                return p.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Failed to translate " + p + " to URL", e);
            }
        }

        private BomDiff diff() {
            return new BomDiff(this);
        }
    }

    public static class VersionChange {
        private boolean upgrade;
        private final Dependency from;
        private final Dependency to;

        private VersionChange(Dependency from, Dependency to, boolean upgrade) {
            this.from = from;
            this.to = to;
            this.upgrade = upgrade;
        }

        public Dependency from() {
            return from;
        }

        public Dependency to() {
            return to;
        }

        public boolean upgrade() {
            return upgrade;
        }
    }

    private final Artifact mainBom;
    private final String mainSource;
    private final URL mainUrl;
    private final Artifact toBom;
    private final String toSource;
    private final URL toUrl;
    private final int mainSize;
    private final int toSize;
    private final List<Dependency> missing;
    private final List<Dependency> extra;
    private final List<Dependency> matching;
    private final List<VersionChange> upgraded;
    private final List<VersionChange> downgraded;

    private BomDiff(Config config) {
        mainBom = config.mainBom;
        mainSource = config.mainSource;
        mainUrl = config.mainUrl;
        toBom = config.toBom;
        toSource = config.toSource;
        toUrl = config.toUrl;
        mainSize = config.mainDeps.size();
        toSize = config.toDeps.size();
        final Map<ArtifactKey, Dependency> mainDeps = toMap(config.mainDeps);
        final Map<ArtifactKey, Dependency> toDeps = toMap(config.toDeps);

        final Map<String, Dependency> missing = new HashMap<>();
        final Map<String, Dependency> extra = new HashMap<>();
        final Map<String, Dependency> matching = new HashMap<>();
        final Map<String, VersionChange> upgraded = new HashMap<>();
        final Map<String, VersionChange> downgraded = new HashMap<>();

        for (Map.Entry<ArtifactKey, Dependency> main : mainDeps.entrySet()) {
            final Dependency toDep = toDeps.remove(main.getKey());
            if (toDep == null) {
                missing.put(main.getKey().toString(), main.getValue());
            } else if (main.getValue().getArtifact().getVersion().equals(toDep.getArtifact().getVersion())) {
                matching.put(main.getKey().toString(), main.getValue());
            } else if (new DefaultArtifactVersion(main.getValue().getArtifact().getVersion())
                    .compareTo(new DefaultArtifactVersion(toDep.getArtifact().getVersion())) > 0) {
                downgraded.put(main.getKey().toString(), new VersionChange(main.getValue(), toDep, false));
            } else {
                upgraded.put(main.getKey().toString(), new VersionChange(main.getValue(), toDep, true));
            }
        }
        toDeps.entrySet().forEach(d -> extra.put(d.getKey().toString(), d.getValue()));

        this.missing = ordered(missing);
        this.extra = ordered(extra);
        this.matching = ordered(matching);
        this.upgraded = ordered(upgraded);
        this.downgraded = ordered(downgraded);
    }

    private <T> List<T> ordered(Map<String, T> map) {
        if (map.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        final List<T> list = new ArrayList<>(map.size());
        keys.forEach(k -> list.add(map.get(k)));
        return list;
    }

    public Artifact mainBom() {
        return mainBom;
    }

    public String mainSource() {
        return mainSource;
    }

    public URL mainUrl() {
        return mainUrl;
    }

    public Artifact toBom() {
        return toBom;
    }

    public String toSource() {
        return toSource;
    }

    public URL toUrl() {
        return toUrl;
    }

    public int mainBomSize() {
        return mainSize;
    }

    public int toBomSize() {
        return toSize;
    }

    public boolean hasMissing() {
        return !missing.isEmpty();
    }

    public List<Dependency> missing() {
        return missing;
    }

    public boolean hasExtra() {
        return !extra.isEmpty();
    }

    public List<Dependency> extra() {
        return extra;
    }

    public boolean hasMatching() {
        return !matching.isEmpty();
    }

    public List<Dependency> matching() {
        return matching;
    }

    public boolean hasUpgraded() {
        return !upgraded.isEmpty();
    }

    public List<VersionChange> upgraded() {
        return upgraded;
    }

    public boolean hasDowngraded() {
        return !downgraded.isEmpty();
    }

    public List<VersionChange> downgraded() {
        return downgraded;
    }

    private static Map<ArtifactKey, Dependency> toMap(List<Dependency> deps) {
        final Map<ArtifactKey, Dependency> map = new HashMap<>(deps.size());
        deps.forEach(d -> map.put(key(d), d));
        return map;
    }

    private static ArtifactKey key(Dependency dep) {
        return ArtifactKey.of(dep.getArtifact().getGroupId(), dep.getArtifact().getArtifactId(),
                dep.getArtifact().getClassifier(), dep.getArtifact().getExtension());
    }
}
