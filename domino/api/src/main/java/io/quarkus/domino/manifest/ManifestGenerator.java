package io.quarkus.domino.manifest;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.resolver.EffectiveModelResolver;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.ReleaseRepo;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
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
import org.eclipse.aether.repository.RemoteRepository;

public class ManifestGenerator {

    public static class Builder {

        private MavenArtifactResolver resolver;
        private Path outputFile;
        private List<SbomTransformer> transformers = List.of();

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

        public Builder addTransformer(SbomTransformer transformer) {
            if (transformers.isEmpty()) {
                transformers = new ArrayList<>(1);
            }
            transformers.add(transformer);
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
    private final EffectiveModelResolver effectiveModelResolver;

    private final Path outputFile;
    private final List<SbomTransformer> transformers;

    private ManifestGenerator(Builder builder) {
        artifactResolver = builder.getInitializedResolver();
        mavenCtx = artifactResolver.getMavenContext();
        effectiveModelResolver = new EffectiveModelResolver(artifactResolver);
        outputFile = builder.outputFile;
        transformers = builder.transformers;
    }

    public Consumer<Collection<ReleaseRepo>> toConsumer() {

        return releases -> {
            Bom bom = new Bom();
            for (ReleaseRepo r : releases) {
                for (Map.Entry<ArtifactCoords, List<RemoteRepository>> entry : r.getArtifacts().entrySet()) {
                    addComponent(bom, r, entry.getKey(), entry.getValue());
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

    private void addComponent(Bom bom, ReleaseRepo release, ArtifactCoords coords, List<RemoteRepository> repos) {
        final Model model = effectiveModelResolver.resolveEffectiveModel(coords, repos);
        final Component c = new Component();
        extractMetadata(release.id(), model, c);
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

        final List<Property> props = new ArrayList<>(2);
        addProperty(props, "package:type", "maven");
        if (!ArtifactCoords.TYPE_POM.equals(coords.getType())) {
            addProperty(props, "package:language", "java");
        }
        c.setProperties(props);
        c.setType(Component.Type.LIBRARY);
        bom.addComponent(c);
    }

    static void addProperty(List<Property> props, String name, String value) {
        var prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        props.add(prop);
    }

    private Bom runTransformers(Bom bom) {
        List<SbomTransformer> transformers = this.transformers;
        final Iterator<SbomTransformer> i = ServiceLoader.load(SbomTransformer.class).iterator();
        if (i.hasNext()) {
            transformers = new ArrayList<>(this.transformers.size() + 2);
            while (i.hasNext()) {
                transformers.add(i.next());
            }
        }
        if (!transformers.isEmpty()) {
            final SbomTransformContextImpl ctx = new SbomTransformContextImpl(bom);
            for (SbomTransformer t : transformers) {
                Bom transformed = t.transform(ctx);
                if (transformed != null) {
                    ctx.bom = transformed;
                }
                bom = ctx.bom;
            }
        }
        return bom;
    }

    static void extractMetadata(ReleaseId releaseId, Model project, Component component) {
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
                addExternalReference(ExternalReference.Type.VCS, releaseId.origin().toString(), component);
            }
        }
    }

    static LicenseChoice resolveMavenLicenses(List<org.apache.maven.model.License> projectLicenses,
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

    static boolean resolveLicenseInfo(LicenseChoice licenseChoice, LicenseChoice licenseChoiceToResolve) {
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

    static Version schemaVersion() {
        return Version.VERSION_14;
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

    static class SbomTransformContextImpl implements SbomTransformContext {

        Bom bom;

        SbomTransformContextImpl(Bom bom) {
            this.bom = bom;
        }

        @Override
        public Bom getOriginalBom() {
            return bom;
        }
    }
}
