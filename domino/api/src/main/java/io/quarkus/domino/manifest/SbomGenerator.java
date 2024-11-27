package io.quarkus.domino.manifest;

import io.quarkus.bom.resolver.EffectiveModelResolver;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.DominoInfo;
import io.quarkus.domino.ProductInfo;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.domino.manifest.ManifestGenerator.SbomTransformContextImpl;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.maven.model.Model;
import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Component.Type;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.cyclonedx.model.ReleaseNotes;
import org.cyclonedx.model.Tool;
import org.eclipse.jgit.util.Hex;
import org.jboss.logging.Logger;

public class SbomGenerator {

    private static final List<String> HASH_ALGS = List.of("MD5", "SHA-1", "SHA-256", "SHA-512", "SHA-384", "SHA3-384",
            "SHA3-256", "SHA3-512");

    private static final Logger log = Logger.getLogger(SbomGenerator.class);

    public class Builder {

        private boolean built;
        private String schemaVersion;

        private Builder() {
        }

        public Builder setArtifactResolver(MavenArtifactResolver resolver) {
            ensureNotBuilt();
            SbomGenerator.this.resolver = resolver;
            return this;
        }

        public MavenArtifactResolver getArtifactResolver() {
            return SbomGenerator.this.resolver;
        }

        public Builder setOutputFile(Path outputFile) {
            ensureNotBuilt();
            SbomGenerator.this.outputFile = outputFile;
            return this;
        }

        public Builder setProductInfo(ProductInfo productInfo) {
            ensureNotBuilt();
            SbomGenerator.this.productInfo = productInfo;
            return this;
        }

        public ProductInfo getProductInfo() {
            return SbomGenerator.this.productInfo;
        }

        public Builder setEnableTransformers(boolean enableTransformers) {
            ensureNotBuilt();
            SbomGenerator.this.enableTransformers = enableTransformers;
            return this;
        }

        public Builder setRecordDependencies(boolean recordDependencies) {
            ensureNotBuilt();
            SbomGenerator.this.recordDependencies = recordDependencies;
            return this;
        }

        public Builder setTopComponents(List<VisitedComponent> topComponents) {
            ensureNotBuilt();
            SbomGenerator.this.topComponents = topComponents;
            return this;
        }

        public Builder setSchemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public SbomGenerator build() {
            ensureNotBuilt();

            if (topComponents == null || topComponents.isEmpty()) {
                throw new IllegalArgumentException("Top components have not been provided");
            }
            if (resolver == null) {
                try {
                    resolver = MavenArtifactResolver.builder().build();
                } catch (BootstrapMavenException e) {
                    throw new IllegalStateException("Failed to initialize Maven artifact resolver", e);
                }
            }
            effectiveModelResolver = new EffectiveModelResolver(resolver);
            SbomGenerator.this.schemaVersion = getSchemaVersion();
            return SbomGenerator.this;
        }

        private Version getSchemaVersion() {
            if (schemaVersion == null) {
                return Collections.max(List.of(Version.values()));
            }
            for (var v : Version.values()) {
                if (schemaVersion.equals(v.getVersionString())) {
                    return v;
                }
            }
            var versions = Version.values();
            var sb = new StringBuilder();
            sb.append("Requested CycloneDX schema version ").append(schemaVersion)
                    .append(" does not appear in the list of supported versions: ")
                    .append(versions[0].getVersionString());
            for (int i = 1; i < versions.length; ++i) {
                sb.append(", ").append(versions[i].getVersionString());
            }
            throw new IllegalArgumentException(sb.toString());
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("This builder instance has already been built");
            }
        }
    }

    public static Builder builder() {
        return new SbomGenerator().new Builder();
    }

    private MavenArtifactResolver resolver;
    private EffectiveModelResolver effectiveModelResolver;
    private Path outputFile;
    private ProductInfo productInfo;
    private boolean enableTransformers;
    private List<VisitedComponent> topComponents;
    private boolean recordDependencies = true;
    private Version schemaVersion;

    private Bom bom;
    private Set<String> addedBomRefs;

    private SbomGenerator() {
    }

    public Bom generate() {

        bom = new Bom();
        addedBomRefs = new HashSet<>();

        var metadata = new Metadata();
        bom.setMetadata(metadata);
        addToolInfo(metadata);

        for (VisitedComponent c : sortAlphabetically(topComponents)) {
            addComponent(c);
        }

        bom = transform(bom);

        addProductInfo(metadata);

        final String bomString;
        try {
            bomString = BomGeneratorFactory.createJson(schemaVersion, bom).toJsonString();
        } catch (GeneratorException e) {
            throw new RuntimeException("Failed to generate an SBOM in JSON format", e);
        }
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
        return bom;
    }

    private void addComponent(VisitedComponent visited) {
        if (visited.getBomRef() == null) {
            throw new IllegalStateException("bom-ref has not been initialized for " + visited.getPurl());
        }
        if (!addedBomRefs.add(visited.getBomRef())) {
            return;
        }
        final Model model = effectiveModelResolver.resolveEffectiveModel(visited.getArtifactCoords(),
                visited.getRepositories());
        final Component c = new Component();
        ManifestGenerator.extractMetadata(visited.getRevision(), model, c, schemaVersion);
        if (c.getPublisher() == null) {
            c.setPublisher("central");
        }
        var coords = visited.getArtifactCoords();
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

        // fix distribution and publisher for components built by RH
        if (RhVersionPattern.isRhVersion(c.getVersion())) {
            PncSbomTransformer.addMrrc(c);
        }
        bom.addComponent(c);

        List<VisitedComponent> dependencies = visited.getDependencies();
        if (!dependencies.isEmpty()) {
            final Dependency d = recordDependencies ? new Dependency(c.getBomRef()) : null;
            for (VisitedComponent child : sortAlphabetically(dependencies)) {
                if (d != null) {
                    d.addDependency(new Dependency(child.getBomRef()));
                }
                addComponent(child);
            }
            if (d != null) {
                bom.addDependency(d);
            }
        }
    }

    private void addProductInfo(Metadata metadata) {
        if (productInfo != null) {
            var group = productInfo.getGroup();
            var name = productInfo.getName();
            var version = productInfo.getVersion();

            var c = new Component();
            if (group != null) {
                c.setGroup(group);
            }
            if (name != null) {
                c.setName(name);
            }
            if (version != null) {
                c.setVersion(version);
            }
            if (productInfo.getPurl() != null) {
                c.setPurl(productInfo.getPurl());
            }
            if (productInfo.getType() != null) {
                c.setType(Type.valueOf(productInfo.getType()));
            }
            if (productInfo.getCpe() != null) {
                c.setCpe(productInfo.getCpe());
            }
            if (productInfo.getDescription() != null) {
                c.setDescription(productInfo.getDescription());
            }

            if (group != null && name != null && version != null) {
                Component addedComponent = null;
                for (VisitedComponent visited : topComponents) {
                    var coords = visited.getArtifactCoords();
                    if (name.equals(coords.getArtifactId()) && group.equals(coords.getGroupId())
                            && version.equals(coords.getVersion())) {
                        for (Component added : bom.getComponents()) {
                            if (added.getBomRef().equals(visited.getBomRef())) {
                                addedComponent = added;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (addedComponent != null) {
                    c.setLicenseChoice(addedComponent.getLicenseChoice());
                    if (c.getPurl() == null) {
                        c.setPurl(addedComponent.getPurl());
                    }
                }
            }

            if (productInfo.getReleaseNotes() != null) {
                var config = productInfo.getReleaseNotes();
                var rn = new ReleaseNotes();
                if (config.getTitle() != null) {
                    rn.setTitle(config.getTitle());
                }
                if (config.getType() != null) {
                    rn.setType(config.getType());
                }
                if (!config.getAliases().isEmpty()) {
                    rn.setAliases(config.getAliases());
                }
                if (!config.getProperties().isEmpty()) {
                    final List<String> names = new ArrayList<>(config.getProperties().keySet());
                    Collections.sort(names);
                    var propList = new ArrayList<Property>(names.size());
                    for (String propName : names) {
                        var prop = new Property();
                        prop.setName(propName);
                        prop.setValue(config.getProperties().get(propName));
                        propList.add(prop);
                    }
                    rn.setProperties(propList);
                }
                c.setReleaseNotes(rn);
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
    }

    private Bom transform(Bom bom) {
        if (enableTransformers) {
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
        }
        return bom;
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

        if (!Files.isDirectory(toolLocation)) {
            final byte[] bytes;
            try {
                bytes = Files.readAllBytes(toolLocation);
            } catch (IOException e) {
                log.warn("Failed to read the tool's binary", e);
                return;
            }

            final List<Hash> hashes = new ArrayList<>(HASH_ALGS.size());
            for (String alg : HASH_ALGS) {
                var hash = getHash(alg, bytes);
                if (hash != null) {
                    hashes.add(hash);
                }
            }
            if (!hashes.isEmpty()) {
                tool.setHashes(hashes);
            }
        } else {
            log.warn("skipping tool hashing because " + toolLocation + " appears to be a directory");
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

    private static List<VisitedComponent> sortAlphabetically(List<VisitedComponent> col) {
        col.sort((o1, o2) -> {
            var coords1 = o1.getArtifactCoords();
            var coords2 = o2.getArtifactCoords();
            int i = coords1.getGroupId().compareTo(coords2.getGroupId());
            if (i != 0) {
                return i;
            }
            i = coords1.getArtifactId().compareTo(coords2.getArtifactId());
            if (i != 0) {
                return i;
            }
            i = coords1.getClassifier().compareTo(coords2.getClassifier());
            if (i != 0) {
                return i;
            }
            i = coords1.getType().compareTo(coords2.getType());
            if (i != 0) {
                return i;
            }
            return coords1.getVersion().compareTo(coords2.getVersion());
        });
        return col;
    }
}
