package io.quarkus.domino.manifest;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.BootstrapModelBuilderFactory;
import io.quarkus.bootstrap.resolver.maven.BootstrapModelResolver;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.domino.ReleaseRepo;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelCache;
import org.apache.maven.model.resolution.ModelResolver;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.CycloneDxSchema.Version;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Property;
import org.cyclonedx.util.LicenseResolver;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;

public class ManifestGenerator {

    public static class Builder {

        private MavenArtifactResolver resolver;
        private Path outputFile;

        private Builder() {
        }

        public Builder setArtifactResolver(MavenArtifactResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        public Builder setOutputFile(Path outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public ManifestGenerator build() {
            return new ManifestGenerator(this);
        }

        private MavenArtifactResolver getInitializedResolver() {
            if (resolver == null) {
                try {
                    return MavenArtifactResolver.builder().build();
                } catch (BootstrapMavenException e) {
                    throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
                }
            }
            return resolver;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final BootstrapMavenContext mavenCtx;
    private final MavenArtifactResolver artifactResolver;
    private final ModelBuilder modelBuilder = BootstrapModelBuilderFactory.getDefaultModelBuilder();
    private final ModelResolver modelResolver;
    private final ModelCache modelCache;

    private final Map<ArtifactCoords, Model> effectiveModels = new HashMap<>();
    private final Path outputFile;

    private ManifestGenerator(Builder builder) {

        this.artifactResolver = builder.getInitializedResolver();
        mavenCtx = artifactResolver.getMavenContext();
        try {
            modelResolver = BootstrapModelResolver.newInstance(mavenCtx, null);
            modelCache = new BootstrapModelCache(mavenCtx.getRepositorySystemSession());
        } catch (BootstrapMavenException e) {
            throw new RuntimeException("Failed to initialize Maven model resolver", e);
        }
        outputFile = builder.outputFile;
    }

    public Consumer<Collection<ReleaseRepo>> toConsumer() {

        return releases -> {
            Bom bom = new Bom();
            for (ReleaseRepo r : releases) {
                for (Map.Entry<ArtifactCoords, List<RemoteRepository>> entry : r.getArtifacts().entrySet()) {
                    ArtifactCoords coords = entry.getKey();
                    if (ArtifactCoords.TYPE_POM.equals(coords.getType())) {
                        continue;
                    }

                    final Model model = resolveModel(modelResolver, coords, entry.getValue());
                    final Component c = new Component();
                    extractMetadata(r, model, c);
                    if (c.getPublisher() == null) {
                        c.setPublisher("central");
                    }
                    c.setGroup(coords.getGroupId());
                    c.setName(coords.getArtifactId());
                    c.setVersion(coords.getVersion());
                    final TreeMap<String, String> qualifiers = new TreeMap<>();
                    qualifiers.put("type", coords.getType());
                    if (!coords.getClassifier().isEmpty()) {
                        qualifiers.put("classifier", coords.getClassifier());
                    }
                    try {
                        c.setPurl(new PackageURL(PackageURL.StandardTypes.MAVEN,
                                coords.getGroupId(),
                                coords.getArtifactId(),
                                coords.getVersion(),
                                qualifiers, null));
                    } catch (MalformedPackageURLException e) {
                        throw new RuntimeException("Failed to generate Purl for " + coords.toCompactCoords(), e);
                    }

                    final Property pkgType = new Property();
                    pkgType.setName("package:type");
                    pkgType.setValue("maven");
                    final Property pkgLang = new Property();
                    pkgLang.setName("package:language");
                    pkgLang.setValue("java");
                    c.setProperties(List.of(pkgType, pkgLang));
                    c.setType(Component.Type.LIBRARY);
                    bom.addComponent(c);
                }
            }

            bom = runTransformers(bom);

            final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(schemaVersion(), bom);
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
        };
    }

    private Bom runTransformers(Bom bom) {
        final Iterator<SbomTransformer> transformers = ServiceLoader.load(SbomTransformer.class).iterator();
        if (transformers.hasNext()) {
            final SbomTransformContextImpl ctx = new SbomTransformContextImpl(bom);
            while (transformers.hasNext()) {
                Bom transformed = transformers.next().transform(ctx);
                if (transformed != null) {
                    ctx.bom = transformed;
                }
            }
            bom = ctx.bom;
        }
        return bom;
    }

    private static void extractMetadata(ReleaseRepo release, Model project, Component component) {
        if (component.getPublisher() == null) {
            // If we don't already have publisher information, retrieve it.
            if (project.getOrganization() != null) {
                component.setPublisher(project.getOrganization().getName());
            }
        }
        if (component.getDescription() == null) {
            // If we don't already have description information, retrieve it.
            component.setDescription(project.getDescription());
        }
        if (component.getLicenseChoice() == null || component.getLicenseChoice().getLicenses() == null
                || component.getLicenseChoice().getLicenses().isEmpty()) {
            // If we don't already have license information, retrieve it.
            if (project.getLicenses() != null) {
                component.setLicenseChoice(resolveMavenLicenses(project.getLicenses(), false));
            }
        }
        if (CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
            if (project.getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.WEBSITE)) {
                    addExternalReference(ExternalReference.Type.WEBSITE, project.getUrl(), component);
                }
            }
            if (project.getCiManagement() != null && project.getCiManagement().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.BUILD_SYSTEM)) {
                    addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.getCiManagement().getUrl(), component);
                }
            }
            if (project.getDistributionManagement() != null && project.getDistributionManagement().getDownloadUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION,
                            project.getDistributionManagement().getDownloadUrl(), component);
                }
            }
            if (project.getDistributionManagement() != null && project.getDistributionManagement().getRepository() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION,
                            project.getDistributionManagement().getRepository().getUrl(), component);
                }
            }
            if (project.getIssueManagement() != null && project.getIssueManagement().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.ISSUE_TRACKER)) {
                    addExternalReference(ExternalReference.Type.ISSUE_TRACKER, project.getIssueManagement().getUrl(),
                            component);
                }
            }
            if (project.getMailingLists() != null && project.getMailingLists().size() > 0) {
                for (MailingList list : project.getMailingLists()) {
                    if (list.getArchive() != null) {
                        if (!doesComponentHaveExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                            addExternalReference(ExternalReference.Type.MAILING_LIST, list.getArchive(), component);
                        }
                    } else if (list.getSubscribe() != null) {
                        if (!doesComponentHaveExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                            addExternalReference(ExternalReference.Type.MAILING_LIST, list.getSubscribe(), component);
                        }
                    }
                }
            }
            if (!doesComponentHaveExternalReference(component, ExternalReference.Type.VCS)) {
                addExternalReference(ExternalReference.Type.VCS, release.id().origin().toString(), component);
            }
        }
    }

    private static LicenseChoice resolveMavenLicenses(List<org.apache.maven.model.License> projectLicenses,
            boolean includeLicenseText) {
        final LicenseChoice licenseChoice = new LicenseChoice();
        for (org.apache.maven.model.License artifactLicense : projectLicenses) {
            boolean resolved = false;
            if (artifactLicense.getName() != null) {
                final LicenseChoice resolvedByName = LicenseResolver.resolve(artifactLicense.getName(), includeLicenseText);
                resolved = resolveLicenseInfo(licenseChoice, resolvedByName);
            }
            if (artifactLicense.getUrl() != null && !resolved) {
                final LicenseChoice resolvedByUrl = LicenseResolver.resolve(artifactLicense.getUrl(), includeLicenseText);
                resolved = resolveLicenseInfo(licenseChoice, resolvedByUrl);
            }
            if (artifactLicense.getName() != null && !resolved) {
                final License license = new License();
                license.setName(artifactLicense.getName().trim());
                if (artifactLicense.getUrl() != null && !artifactLicense.getUrl().isBlank()) {
                    try {
                        final URI uri = new URI(artifactLicense.getUrl().trim());
                        license.setUrl(uri.toString());
                    } catch (URISyntaxException e) {
                        // throw it away
                    }
                }
                licenseChoice.addLicense(license);
            }
        }
        return licenseChoice;
    }

    private static boolean resolveLicenseInfo(LicenseChoice licenseChoice, LicenseChoice licenseChoiceToResolve) {
        if (licenseChoiceToResolve != null) {
            if (licenseChoiceToResolve.getLicenses() != null && !licenseChoiceToResolve.getLicenses().isEmpty()) {
                licenseChoice.addLicense(licenseChoiceToResolve.getLicenses().get(0));
                return true;
            } else if (licenseChoiceToResolve.getExpression() != null &&
                    Version.VERSION_10 != schemaVersion()) {
                licenseChoice.setExpression(licenseChoiceToResolve.getExpression());
                return true;
            }
        }
        return false;
    }

    private static Version schemaVersion() {
        return Version.VERSION_14;
    }

    private Model resolveModel(ModelResolver modelResolver, ArtifactCoords artifact, List<RemoteRepository> repos) {
        final ArtifactCoords pom = artifact.getType().equals(ArtifactCoords.TYPE_POM) ? artifact
                : ArtifactCoords.pom(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        return effectiveModels.computeIfAbsent(pom, k -> {
            File pomFile;
            try {
                pomFile = artifactResolver.resolve(new DefaultArtifact(pom.getGroupId(), pom.getArtifactId(),
                        pom.getClassifier(), pom.getType(), pom.getVersion()), repos).getArtifact().getFile();
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to resolve " + pom.toCompactCoords(), e);
            }

            final Model rawModel;
            try {
                rawModel = ModelUtils.readModel(pomFile.toPath());
            } catch (IOException e1) {
                throw new RuntimeException("Failed to read " + pomFile, e1);
            }

            // override the relative path to the parent in case it's in the local Maven repo
            Parent parent = rawModel.getParent();
            if (parent != null) {
                final Artifact parentPom = new DefaultArtifact(parent.getGroupId(), parent.getArtifactId(),
                        ArtifactCoords.TYPE_POM, parent.getVersion());
                final Path parentPomPath;
                try {
                    parentPomPath = artifactResolver.resolve(parentPom, repos).getArtifact().getFile().toPath();
                } catch (BootstrapMavenException e) {
                    throw new RuntimeException("Failed to resolve " + parentPom, e);
                }
                rawModel.getParent().setRelativePath(pomFile.toPath().getParent().relativize(parentPomPath).toString());
            }

            ModelBuildingRequest req = new DefaultModelBuildingRequest();
            req.setPomFile(pomFile);
            req.setRawModel(rawModel);
            req.setModelResolver(modelResolver);
            req.setSystemProperties(System.getProperties());
            req.setUserProperties(System.getProperties());
            req.setModelCache(modelCache);

            try {
                return modelBuilder.build(req).getEffectiveModel();
            } catch (ModelBuildingException e) {
                throw new RuntimeException("Failed to resolve the effective model of " + pom.toCompactCoords(), e);
            }
        });
    }

    private static boolean doesComponentHaveExternalReference(final Component component, final ExternalReference.Type type) {
        if (component.getExternalReferences() != null && !component.getExternalReferences().isEmpty()) {
            for (final ExternalReference ref : component.getExternalReferences()) {
                if (type == ref.getType()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addExternalReference(final ExternalReference.Type referenceType, final String url,
            final Component component) {
        try {
            final URI uri = new URI(url.trim());
            final ExternalReference ref = new ExternalReference();
            ref.setType(referenceType);
            ref.setUrl(uri.toString());
            component.addExternalReference(ref);
        } catch (URISyntaxException e) {
            // throw it away
        }
    }

    static class BootstrapModelCache implements ModelCache {

        private final RepositorySystemSession session;

        private final RepositoryCache cache;

        BootstrapModelCache(RepositorySystemSession session) {
            this.session = session;
            this.cache = session.getCache() == null ? new DefaultRepositoryCache() : session.getCache();
        }

        @Override
        public Object get(String groupId, String artifactId, String version, String tag) {
            return cache.get(session, new Key(groupId, artifactId, version, tag));
        }

        @Override
        public void put(String groupId, String artifactId, String version, String tag, Object data) {
            cache.put(session, new Key(groupId, artifactId, version, tag), data);
        }

        static class Key {

            private final String groupId;
            private final String artifactId;
            private final String version;
            private final String tag;
            private final int hash;

            public Key(String groupId, String artifactId, String version, String tag) {
                this.groupId = groupId;
                this.artifactId = artifactId;
                this.version = version;
                this.tag = tag;

                int h = 17;
                h = h * 31 + this.groupId.hashCode();
                h = h * 31 + this.artifactId.hashCode();
                h = h * 31 + this.version.hashCode();
                h = h * 31 + this.tag.hashCode();
                hash = h;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (null == obj || !getClass().equals(obj.getClass())) {
                    return false;
                }

                Key that = (Key) obj;
                return artifactId.equals(that.artifactId) && groupId.equals(that.groupId)
                        && version.equals(that.version) && tag.equals(that.tag);
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }
    }

    private static class SbomTransformContextImpl implements SbomTransformContext {

        private Bom bom;

        private SbomTransformContextImpl(Bom bom) {
            this.bom = bom;
        }

        @Override
        public Bom getOriginalBom() {
            return bom;
        }
    }
}
