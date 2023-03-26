package io.quarkus.domino.manifest;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.BootstrapModelBuilderFactory;
import io.quarkus.bootstrap.resolver.maven.BootstrapModelResolver;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalWorkspace;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.domino.DependencyTreeVisitor;
import io.quarkus.domino.DominoInfo;
import io.quarkus.domino.manifest.ManifestGenerator.BootstrapModelCache;
import io.quarkus.domino.manifest.ManifestGenerator.SbomTransformContextImpl;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelCache;
import org.apache.maven.model.resolution.ModelResolver;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Component.Type;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.cyclonedx.model.Tool;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.jgit.util.Hex;
import org.jboss.logging.Logger;

public class SbomGeneratingDependencyVisitor implements DependencyTreeVisitor {

    private static final Logger log = Logger.getLogger(SbomGeneratingDependencyVisitor.class);

    private final MavenArtifactResolver resolver;
    private final Path outputFile;
    private final ProductInfo productInfo;
    private final boolean enableSbomTransformers;
    private final Map<ArtifactCoords, List<VisitedComponent>> visitedComponents = new HashMap<>();
    private final ArrayDeque<VisitedComponent> componentStack = new ArrayDeque<>();

    private final ModelBuilder modelBuilder = BootstrapModelBuilderFactory.getDefaultModelBuilder();
    private final ModelCache modelCache;
    private final Map<ArtifactCoords, Model> effectiveModels = new HashMap<>();

    public SbomGeneratingDependencyVisitor(MavenArtifactResolver resolver, Path outputFile, ProductInfo productInfo,
            boolean enableSbomTransformers) {
        this.resolver = resolver;
        this.outputFile = outputFile;
        this.productInfo = productInfo;
        this.enableSbomTransformers = enableSbomTransformers;
        try {
            modelCache = new BootstrapModelCache(resolver.getMavenContext().getRepositorySystemSession());
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to initialize Maven model resolver", e);
        }
    }

    @Override
    public void beforeAllRoots() {
    }

    @Override
    public void afterAllRoots() {
        Bom bom = new Bom();

        var metadata = new Metadata();
        bom.setMetadata(metadata);
        addToolInfo(metadata);

        if (productInfo != null) {
            var c = new Component();
            if (productInfo.getGroup() != null) {
                c.setGroup(productInfo.getGroup());
            }
            if (productInfo.getName() != null) {
                c.setName(productInfo.getName());
            }
            if (productInfo.getVersion() != null) {
                c.setVersion(productInfo.getVersion());
            }
            if (productInfo.getType() != null) {
                c.setType(Type.valueOf(productInfo.getType()));
            }
            if (productInfo.getId() != null) {
                var prop = new Property();
                prop.setName("product-id");
                prop.setValue(productInfo.getId());
                c.addProperty(prop);
            }
            if (productInfo.getStream() != null) {
                var prop = new Property();
                prop.setName("product-stream");
                prop.setValue(productInfo.getStream());
                c.addProperty(prop);
            }
            metadata.setComponent(c);
        }

        for (ArtifactCoords coords : sortAlphabetically(visitedComponents.keySet())) {
            for (VisitedComponent c : visitedComponents.get(coords)) {
                addComponent(bom, c);
            }
        }

        if (enableSbomTransformers) {
            bom = runTransformers(bom);
        }

        final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(ManifestGenerator.schemaVersion(), bom);
        final String bomString = bomGenerator.toJsonString();
        if (outputFile == null) {
            System.out.println(bomString);
        } else {
            if (outputFile.getParent() != null) {
                try {
                    Files.createDirectories(outputFile.getParent());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create " + outputFile.getParent(), e);
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
                writer.write(bomString);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to " + outputFile, e);
            }
        }
    }

    private void addToolInfo(Metadata metadata) {
        var tool = new Tool();
        tool.setName(DominoInfo.PROJECT_NAME);
        tool.setVendor(DominoInfo.ORGANIZATION_NAME);
        tool.setVersion(DominoInfo.VERSION);
        metadata.setTools(List.of(tool));

        var toolLocation = getToolLocation();
        if (toolLocation == null) {
            return;
        }

        String toolName = toolLocation.getFileName().toString();
        if (toolName.endsWith(".jar")) {
            toolName = toolName.substring(0, toolName.length() - ".jar".length());
        }
        String[] parts = toolName.split("-");
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; ++i) {
            var s = parts[i];
            if (s.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(s.charAt(0)));
            if (s.length() > 1) {
                sb.append(s.substring(1));
            }
            sb.append(' ');
        }
        tool.setName(sb.append("SBOM Generator").toString());

        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(toolLocation);
        } catch (IOException e) {
            log.warn("Failed to read the tool's binary", e);
            return;
        }

        final List<String> algs = List.of("MD5", "SHA-1", "SHA-256", "SHA-512", "SHA-384", "SHA3-384", "SHA3-256", "SHA3-512");
        final List<Hash> hashes = new ArrayList<>(algs.size());
        for (String alg : algs) {
            var hash = getHash(alg, bytes);
            if (hash != null) {
                hashes.add(hash);
            }
        }
        if (hashes != null) {
            tool.setHashes(hashes);
        }
    }

    private static Hash getHash(String alg, byte[] content) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to initialize a message digest with algorithm " + alg + ": " + e.getLocalizedMessage());
            return null;
        }
        return new Hash(md.getAlgorithm(), Hex.toHexString(md.digest(content)));
    }

    private Path getToolLocation() {
        var cs = getClass().getProtectionDomain().getCodeSource();
        if (cs == null) {
            log.warn("Failed to determine code source of the tool");
            return null;
        }
        var url = cs.getLocation();
        if (url == null) {
            log.warn("Failed to determine code source URL of the tool");
            return null;
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            log.warn("Failed to translate " + url + " to a file system path", e);
            return null;
        }
    }

    private static List<ArtifactCoords> sortAlphabetically(Collection<ArtifactCoords> col) {
        final List<ArtifactCoords> list = new ArrayList<>(col);
        Collections.sort(list, new Comparator<ArtifactCoords>() {
            @Override
            public int compare(ArtifactCoords o1, ArtifactCoords o2) {
                int i = o1.getGroupId().compareTo(o2.getGroupId());
                if (i != 0) {
                    return i;
                }
                i = o1.getArtifactId().compareTo(o2.getArtifactId());
                if (i != 0) {
                    return i;
                }
                i = o1.getClassifier().compareTo(o2.getClassifier());
                if (i != 0) {
                    return i;
                }
                i = o1.getType().compareTo(o2.getType());
                if (i != 0) {
                    return i;
                }
                return o1.getVersion().compareTo(o2.getVersion());
            }
        });
        return list;
    }

    private Bom runTransformers(Bom bom) {
        final Iterator<SbomTransformer> i = ServiceLoader.load(SbomTransformer.class).iterator();
        if (i.hasNext()) {
            final SbomTransformContextImpl ctx = new SbomTransformContextImpl(bom);
            while (i.hasNext()) {
                Bom transformed = i.next().transform(ctx);
                if (transformed != null) {
                    ctx.bom = transformed;
                }
                bom = ctx.bom;
            }
        }
        return bom;
    }

    private void addComponent(Bom bom, VisitedComponent visited) {
        final ArtifactCoords coords = visited.coords;
        final Model model = resolveModel(visited);
        final Component c = new Component();
        ManifestGenerator.extractMetadata(visited.releaseId, model, c);
        if (c.getPublisher() == null) {
            c.setPublisher("central");
        }
        c.setGroup(coords.getGroupId());
        c.setName(coords.getArtifactId());
        c.setVersion(coords.getVersion());
        c.setPurl(visited.getPurl());
        c.setBomRef(visited.getBomRef());

        final List<Property> props = new ArrayList<>(2);
        ManifestGenerator.addProperty(props, "package:type", "maven");
        if (!ArtifactCoords.TYPE_POM.equals(coords.getType())) {
            ManifestGenerator.addProperty(props, "package:language", "java");
        }
        c.setProperties(props);
        c.setType(Component.Type.LIBRARY);
        bom.addComponent(c);

        if (!visited.directCompDeps.isEmpty()) {
            final Dependency d = new Dependency(c.getBomRef());
            for (VisitedComponent dd : visited.directCompDeps) {
                d.addDependency(new Dependency(dd.getBomRef()));
            }
            bom.addDependency(d);
        }
    }

    private Model resolveModel(VisitedComponent c) {
        final ArtifactCoords pom = c.coords.getType().equals(ArtifactCoords.TYPE_POM) ? c.coords
                : ArtifactCoords.pom(c.coords.getGroupId(), c.coords.getArtifactId(), c.coords.getVersion());
        return effectiveModels.computeIfAbsent(pom, p -> doResolveModel(pom, c.repos));
    }

    private Model doResolveModel(ArtifactCoords coords, List<RemoteRepository> repos) {

        final LocalWorkspace ws = resolver.getMavenContext().getWorkspace();
        if (ws != null) {
            final LocalProject project = ws.getProject(coords.getGroupId(), coords.getArtifactId());
            if (project != null && coords.getVersion().equals(project.getVersion())
                    && project.getModelBuildingResult() != null) {
                return project.getModelBuildingResult().getEffectiveModel();
            }
        }

        final File pomFile;
        final ArtifactResult pomResult;
        try {
            pomResult = resolver.resolve(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(),
                    coords.getClassifier(), coords.getType(), coords.getVersion()), repos);
            pomFile = pomResult.getArtifact().getFile();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to resolve " + coords.toCompactCoords(), e);
        }

        final Model rawModel;
        try {
            rawModel = ModelUtils.readModel(pomFile.toPath());
        } catch (IOException e1) {
            throw new RuntimeException("Failed to read " + pomFile, e1);
        }

        final ModelResolver modelResolver;
        try {
            modelResolver = BootstrapModelResolver.newInstance(resolver.getMavenContext(), null);
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to initialize model resolver", e);
        }

        // override the relative path to the parent in case it's in the local Maven repo
        Parent parent = rawModel.getParent();
        if (parent != null) {
            final Artifact parentPom = new DefaultArtifact(parent.getGroupId(), parent.getArtifactId(),
                    ArtifactCoords.TYPE_POM, parent.getVersion());
            final ArtifactResult parentResult;
            final Path parentPomPath;
            try {
                parentResult = resolver.resolve(parentPom, repos);
                parentPomPath = parentResult.getArtifact().getFile().toPath();
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to resolve " + parentPom, e);
            }
            rawModel.getParent().setRelativePath(pomFile.toPath().getParent().relativize(parentPomPath).toString());

            String repoUrl = null;
            for (RemoteRepository r : repos) {
                if (r.getId().equals(parentResult.getRepository().getId())) {
                    repoUrl = r.getUrl();
                    break;
                }
            }
            if (repoUrl != null) {
                Repository modelRepo = null;
                for (Repository r : rawModel.getRepositories()) {
                    if (r.getId().equals(parentResult.getRepository().getId())) {
                        modelRepo = r;
                        break;
                    }
                }
                if (modelRepo == null) {
                    modelRepo = new Repository();
                    modelRepo.setId(parentResult.getRepository().getId());
                    modelRepo.setLayout("default");
                    modelRepo.setReleases(new RepositoryPolicy());
                }
                modelRepo.setUrl(repoUrl);

                try {
                    modelResolver.addRepository(modelRepo, false);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to add repository " + modelRepo, e);
                }
            }
        }

        final ModelBuildingRequest req = new DefaultModelBuildingRequest();
        req.setPomFile(pomFile);
        req.setRawModel(rawModel);
        req.setModelResolver(modelResolver);
        req.setSystemProperties(System.getProperties());
        req.setUserProperties(System.getProperties());
        req.setModelCache(modelCache);

        try {
            return modelBuilder.build(req).getEffectiveModel();
        } catch (ModelBuildingException e) {
            throw new RuntimeException("Failed to resolve the effective model of " + coords.toCompactCoords(), e);
        }
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        enterComponent(visit);
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        leaveComponent(visit);
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
        enterComponent(visit);
    }

    @Override
    public void leaveDependency(DependencyVisit visit) {
        leaveComponent(visit);
    }

    @Override
    public void enterParentPom(DependencyVisit visit) {
    }

    @Override
    public void leaveParentPom(DependencyVisit visit) {
    }

    @Override
    public void enterBomImport(DependencyVisit visit) {
    }

    @Override
    public void leaveBomImport(DependencyVisit visit) {
    }

    private void enterComponent(DependencyVisit visit) {
        var current = componentStack.peek();
        var next = new VisitedComponent(visit);
        if (current != null) {
            current.directDeps.add(visit.getCoords());
        }
        componentStack.push(next);
    }

    private void leaveComponent(DependencyVisit visit) {
        var current = componentStack.pop();
        var parent = componentStack.peek();
        final List<VisitedComponent> variants = visitedComponents.computeIfAbsent(current.coords, k -> new ArrayList<>(4));
        for (VisitedComponent variant : variants) {
            if (variant.directDeps.equals(current.directDeps)) {
                if (parent != null) {
                    parent.directCompDeps.add(variant);
                }
                return;
            }
        }

        if (variants.size() == 1) {
            variants.get(0).generateBomRef(variants.size());
        } else if (!variants.isEmpty()) {
            current.generateBomRef(variants.size() + 1);
        }
        variants.add(current);
        if (parent != null) {
            parent.directCompDeps.add(current);
        }
    }

    private static class VisitedComponent {
        private final ReleaseId releaseId;
        private final ArtifactCoords coords;
        private final List<RemoteRepository> repos;
        private final Set<ArtifactCoords> directDeps = new HashSet<>();
        private final List<VisitedComponent> directCompDeps = new ArrayList<>();
        private String bomRef;
        private PackageURL purl;

        VisitedComponent(DependencyVisit visit) {
            this.releaseId = visit.getReleaseId();
            this.coords = visit.getCoords();
            this.repos = visit.getRepositories();
        }

        void generateBomRef(int index) {
            if (bomRef == null) {
                var sb = new StringBuilder();
                String[] parts = coords.getGroupId().split("\\.");
                sb.append(parts[0].charAt(0));
                for (int i = 1; i < parts.length; ++i) {
                    sb.append('.').append(parts[i].charAt(0));
                }
                sb.append(':').append(coords.getArtifactId()).append(':');
                if (!coords.getClassifier().isEmpty()) {
                    sb.append(coords.getClassifier()).append(':');
                }
                if (!coords.getType().equals(ArtifactCoords.TYPE_JAR)) {
                    sb.append(coords.getType()).append(':');
                }
                sb.append(coords.getVersion()).append('#').append(index);
                //bomRef = UUID.randomUUID().toString();
                bomRef = sb.toString();
            }
        }

        String getBomRef() {
            return bomRef == null ? bomRef = getPurl().toString() : bomRef;
        }

        PackageURL getPurl() {
            if (purl == null) {
                final TreeMap<String, String> qualifiers = new TreeMap<>();
                qualifiers.put("type", coords.getType());
                if (!coords.getClassifier().isEmpty()) {
                    qualifiers.put("classifier", coords.getClassifier());
                }
                try {
                    purl = new PackageURL(PackageURL.StandardTypes.MAVEN,
                            coords.getGroupId(),
                            coords.getArtifactId(),
                            coords.getVersion(),
                            qualifiers, null);
                } catch (MalformedPackageURLException e) {
                    throw new RuntimeException("Failed to generate Purl for " + coords.toCompactCoords(), e);
                }
            }
            return purl;
        }
    }
}
