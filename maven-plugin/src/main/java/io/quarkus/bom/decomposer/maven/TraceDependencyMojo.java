package io.quarkus.bom.decomposer.maven;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.platform.tools.ToolsUtils;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.ExtensionOrigin;
import io.quarkus.registry.util.PlatformArtifacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

@Mojo(name = "trace", threadSafe = true, requiresProject = false)
public class TraceDependencyMojo extends AbstractMojo {

    private static final String COM_REDHAT_QUARKUS_PLATFORM = "com.redhat.quarkus.platform";
    private static final String ARROW = "\u21b3";

    @Component
    RemoteRepositoryManager remoteRepoManager;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

    /**
     * Complete coordinates of the dependency to trace, e.g. a GAV or a GACTV.
     * Either this or {@link #key} can be specified at the same time.
     */
    @Parameter(property = "dependency")
    String dependency;

    /**
     * Dependency key to trace, e.g. a GA or a GACT.
     * Either this or {@link #dependency} can be specified at the same time.
     */
    @Parameter(property = "key")
    String key;

    /**
     * This could either be a platform version or a complete GAV of a platform BOM.
     * For versions with the `redhat` suffix, `com.redhat.quarkus.platform` groupId will be used.
     * For other versions it will be `io.quarkus.platform`.
     */
    @Parameter(property = "release", required = true)
    String release;

    /**
     * This option indicates whether only the extensions with the redhat-support metadata present in their
     * descriptors should be analyzed.
     */
    @Parameter(property = "redhatSupported")
    boolean redhatSupported;

    /**
     * Detailed option enables logging the relevant dependency branch from the extension root artifact
     * to the dependency being traced.
     */
    @Parameter(property = "detailed")
    boolean detailed;

    /**
     * Deployment option enables tracing dependencies in the deployment classpath
     */
    @Parameter(property = "deployment")
    boolean deployment;

    private MavenArtifactResolver resolver;
    private Map<ArtifactCoords, List<Dependency>> platformManagedDeps = new HashMap<>();
    private Map<ArtifactCoords, Map<ArtifactCoords, TargetInfo>> traces = new HashMap<>();
    private Map<ArtifactCoords, ArtifactCoords> bomTraces = new HashMap<>();
    private ArtifactCoords dependencyCoords;
    private ArtifactKey dependencyKey;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (dependency != null) {
            dependencyCoords = ArtifactCoords.fromString(dependency);
            if (key != null) {
                throw new MojoExecutionException("Only one of 'dependency' or 'key' can be set at the same time");
            }
        } else {
            if (key == null) {
                throw new MojoExecutionException("Neither 'dependency' nor 'key' parameter was set");
            }
            dependencyKey = ArtifactKey.fromString(key);
        }

        final ExtensionCatalog extensionCatalog = resolveExtensionCatalog();
        final ArtifactCoords quarkusBom = getQuarkusBom(extensionCatalog);
        for (Extension e : extensionCatalog.getExtensions()) {
            processExtension(e, quarkusBom);
        }

        if (bomTraces.isEmpty()) {
            getLog().info("None of the BOMs include " + traceTarget());
        } else {
            getLog().info("The following BOMs include " + traceTarget() + ":");
            for (Map.Entry<ArtifactCoords, ArtifactCoords> c : bomTraces.entrySet()) {
                getLog().info("  " + c.getKey().toCompactCoords());
                if (isLogTraceTarget()) {
                    getLog().info("  " + ARROW + " " + c.getValue().toCompactCoords());
                }
            }
            getLog().info("");
        }

        if (traces.isEmpty()) {
            getLog().info("None of the extensions depend on " + traceTarget());
        } else {
            getLog().info("The following extensions depend on " + traceTarget() + ":");
            for (Map.Entry<ArtifactCoords, Map<ArtifactCoords, TargetInfo>> trace : traces.entrySet()) {
                getLog().info("  Extensions included in " + trace.getKey().toCompactCoords() + ":");
                for (Map.Entry<ArtifactCoords, TargetInfo> e : trace.getValue().entrySet()) {
                    if (detailed) {
                        for (int i = 0; i < e.getValue().chain.length; ++i) {
                            final StringBuilder sb = new StringBuilder("    ");
                            for (int j = 0; j < i - 1; ++j) {
                                sb.append("  ");
                            }
                            if (i > 0) {
                                sb.append(ARROW).append(" ");
                            }
                            getLog().info(sb.append(e.getValue().chain[i].toCompactCoords()));
                        }
                    } else {
                        getLog().info("    " + e.getKey().toCompactCoords());
                        if (isLogTraceTarget()) {
                            getLog().info("    " + ARROW + " " + e.getValue().target.toCompactCoords());
                        }
                    }
                }
            }
        }
    }

    private boolean isLogTraceTarget() {
        return dependency == null;
    }

    private Object traceTarget() {
        if (dependency == null) {
            return key;
        }
        return dependency;
    }

    private void processExtension(Extension e, ArtifactCoords quarkusBom) throws MojoExecutionException {
        if (redhatSupported && !RhVersionPattern.isRhVersion(e.getArtifact().getVersion())) {
            return;
        }
        final ArtifactCoords platformBom = getPlatformOrigin(e);
        if (platformBom == null) {
            return;
        }
        final List<Dependency> managedDeps = getPlatformManagedDeps(platformBom, quarkusBom);
        TargetInfo found = walkLooking(collectDeps(toAetherArtifact(e.getArtifact()), managedDeps), 0);
        if (found == null && deployment) {
            found = walkLooking(collectDeps(toAetherDeploymentArtifact(e.getArtifact()), managedDeps), 0);
        }
        if (found != null) {
            traces.computeIfAbsent(platformBom, k -> new HashMap<>()).put(e.getArtifact(), found);
        }
    }

    private TargetInfo walkLooking(DependencyNode node, int depth) throws MojoExecutionException {
        Artifact a = node.getArtifact();
        if (a != null) {
            if (matchesTraceTarget(a)) {
                return new TargetInfo(toCoords(a), depth);
            }
        }
        for (DependencyNode c : node.getChildren()) {
            TargetInfo target = walkLooking(c, depth + 1);
            if (target != null) {
                target.chain[depth] = toCoords(a);
                return target;
            }
        }
        return null;
    }

    private ArtifactCoords toCoords(Artifact a) {
        return ArtifactCoords.of(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
    }

    private boolean matchesTraceTarget(Artifact a) {
        if (dependencyCoords != null) {
            return a.getArtifactId().equals(dependencyCoords.getArtifactId())
                    && a.getGroupId().equals(dependencyCoords.getGroupId())
                    && a.getExtension().equals(dependencyCoords.getType())
                    && a.getVersion().equals(dependencyCoords.getVersion())
                    && a.getClassifier().equals(dependencyCoords.getClassifier());
        }
        if (dependencyKey != null) {
            return a.getArtifactId().equals(dependencyKey.getArtifactId())
                    && a.getGroupId().equals(dependencyKey.getGroupId())
                    && a.getExtension().equals(dependencyKey.getType())
                    && a.getClassifier().equals(dependencyKey.getClassifier());
        }
        throw new IllegalStateException("Neither dependencyCoords nor dependencyKey is initialized");
    }

    private DependencyNode collectDeps(Artifact a, List<Dependency> managedDeps) throws MojoExecutionException {
        try {
            return resolver().collectManagedDependencies(a, List.of(), managedDeps, List.of(), List.of(), JavaScopes.TEST,
                    JavaScopes.PROVIDED).getRoot();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to collect dependencies of " + a, e);
        }
    }

    private List<Dependency> getPlatformManagedDeps(ArtifactCoords bom, ArtifactCoords quarkusBom)
            throws MojoExecutionException {
        List<Dependency> managedDeps = platformManagedDeps.get(bom);
        if (managedDeps != null) {
            return managedDeps;
        }
        List<Dependency> quarkusManagedDeps = platformManagedDeps.get(quarkusBom);
        if (quarkusManagedDeps == null) {
            quarkusManagedDeps = getManagedDeps(quarkusBom);
            platformManagedDeps.put(quarkusBom, quarkusManagedDeps);
            if (bom.equals(quarkusBom)) {
                return quarkusManagedDeps;
            }
        }
        List<Dependency> tmp = getManagedDeps(bom);
        managedDeps = new ArrayList<>(quarkusManagedDeps.size() + tmp.size());
        managedDeps.addAll(quarkusManagedDeps);
        managedDeps.addAll(tmp);
        platformManagedDeps.put(bom, managedDeps);
        return managedDeps;
    }

    private List<Dependency> getManagedDeps(ArtifactCoords bom) throws MojoExecutionException {
        try {
            final List<Dependency> managedDeps = resolver().resolveDescriptor(toAetherArtifact(bom)).getManagedDependencies();
            for (Dependency d : managedDeps) {
                final Artifact a = d.getArtifact();
                if (matchesTraceTarget(a)
                        && (!redhatSupported || bom.getGroupId().equals(COM_REDHAT_QUARKUS_PLATFORM))) {
                    bomTraces.put(bom, toCoords(a));
                    break;
                }
            }
            return managedDeps;
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to read artifact descriptor for " + bom, e);
        }
    }

    private DefaultArtifact toAetherArtifact(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(), coords.getType(),
                coords.getVersion());
    }

    private DefaultArtifact toAetherDeploymentArtifact(ArtifactCoords coords) {
        return new DefaultArtifact(coords.getGroupId(), coords.getArtifactId() + "-deployment", coords.getClassifier(),
                coords.getType(), coords.getVersion());
    }

    private ArtifactCoords getPlatformOrigin(Extension e) {
        for (ExtensionOrigin o : e.getOrigins()) {
            if (o.isPlatform()) {
                return o.getBom();
            }
        }
        return null;
    }

    private ArtifactCoords getQuarkusBom(ExtensionCatalog extensionCatalog) {
        for (ExtensionOrigin o : extensionCatalog.getDerivedFrom()) {
            if (o.getBom().getArtifactId().equals("quarkus-bom")) {
                return o.getBom();
            }
        }
        final StringJoiner sj = new StringJoiner(", ");
        extensionCatalog.getDerivedFrom().forEach(o -> sj.add(o.getBom().toCompactCoords()));
        throw new IllegalStateException("Failed to locate quarkus-bom among " + sj.toString());
    }

    private ExtensionCatalog resolveExtensionCatalog() throws MojoExecutionException {
        final ArtifactCoords platformCoords;
        if (release.contains(":")) {
            platformCoords = PlatformArtifacts.ensureBomArtifact(ArtifactCoords.fromString(release));
        } else if (RhVersionPattern.isRhVersion(release)) {
            platformCoords = ArtifactCoords.pom(COM_REDHAT_QUARKUS_PLATFORM, "quarkus-bom", release);
        } else {
            platformCoords = ArtifactCoords.pom("io.quarkus.platform", "quarkus-bom", release);
        }
        return ToolsUtils.resolvePlatformDescriptorDirectly(platformCoords.getGroupId(), platformCoords.getArtifactId(),
                platformCoords.getVersion(), resolver(), new MojoMessageWriter(getLog()));
    }

    private MavenArtifactResolver resolver() throws MojoExecutionException {
        if (resolver != null) {
            return resolver;
        }
        try {
            return resolver = MavenArtifactResolver.builder()
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .setRepositorySystem(repoSystem)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize the Maven resolver");
        }
    }

    private static class TargetInfo {
        final ArtifactCoords target;
        final ArtifactCoords[] chain;

        private TargetInfo(ArtifactCoords target, int depth) {
            this.target = target;
            chain = new ArtifactCoords[depth + 1];
            chain[depth] = target;
        }
    }
}
