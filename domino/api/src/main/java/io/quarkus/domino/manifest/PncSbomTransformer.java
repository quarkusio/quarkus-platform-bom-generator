package io.quarkus.domino.manifest;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.domino.manifest.PncArtifactBuildInfo.Content;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.Hash.Algorithm;
import org.cyclonedx.model.Property;
import org.jboss.logging.Logger;

public class PncSbomTransformer implements SbomTransformer {

    private static final String PNC = "PNC";
    private static final String BUILD_SYSTEM = "build-system";
    private static final String BUILD_ID = "build-id";
    private static final String PUBLISHER = "redhat";
    private static final String MRRC_URL = "https://maven.repository.redhat.com/ga/";

    private static final Logger log = Logger.getLogger(PncSbomTransformer.class);

    @Override
    public Bom transform(SbomTransformContext ctx) {
        log.info("Adding PNC build info to a manifest");
        final Bom bom = ctx.getOriginalBom();
        if (bom.getComponents() == null) {
            return bom;
        }
        for (Component c : bom.getComponents()) {
            if (RhVersionPattern.isRhVersion(c.getVersion())) {

                // PNC does not support artifact classifiers in purl,
                // so here I am using the POM purl to pull in the build info
                final TreeMap<String, String> qualifiers = new TreeMap<>();
                qualifiers.put("type", "pom");
                final PackageURL purl;
                try {
                    purl = new PackageURL(PackageURL.StandardTypes.MAVEN,
                            c.getGroup(),
                            c.getName(),
                            c.getVersion(),
                            qualifiers, null);
                } catch (MalformedPackageURLException e) {
                    throw new RuntimeException("Failed to generate Purl for " + c, e);
                }

                final URL url;
                try {
                    url = new URL("https", "orch.psi.redhat.com", 443,
                            "/pnc-rest/v2/artifacts?q=purl==%22" + purl + "%22");
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Failed to parse URL", e);
                }
                final PncArtifactBuildInfo buildInfo;
                try {
                    final URLConnection connection = url.openConnection();
                    buildInfo = PncArtifactBuildInfo.deserialize(connection.getInputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to connect to " + url, e);
                }
                final Content content = getContent(buildInfo);
                if (content == null) {
                    log.warn("PNC build info not found for " + url);
                } else {
                    final List<Hash> hashes = new ArrayList<>(3);
                    if (content.getMd5() != null) {
                        hashes.add(new Hash(Algorithm.MD5, content.getMd5()));
                    }
                    if (content.getSha1() != null) {
                        hashes.add(new Hash(Algorithm.SHA1, content.getSha1()));
                    }
                    if (content.getSha256() != null) {
                        hashes.add(new Hash(Algorithm.SHA_256, content.getSha256()));
                    }
                    c.setHashes(hashes);

                    if (content.getBuild() == null) {
                        log.warn("PNC build info not found for " + url);
                    } else {
                        final List<Property> props = new ArrayList<>(c.getProperties());

                        Property prop = new Property();
                        prop.setName(BUILD_ID);
                        prop.setValue(content.getBuild().getId());
                        props.add(prop);

                        prop = new Property();
                        prop.setName(BUILD_SYSTEM);
                        prop.setValue(PNC);
                        props.add(prop);
                        c.setProperties(props);
                    }
                }

                addMrrc(c);
            }
        }
        return bom;
    }

    private void addMrrc(Component c) {
        c.setPublisher(PUBLISHER);
        List<ExternalReference> externalRefs = new ArrayList<>(c.getExternalReferences());
        ExternalReference dist = null;
        for (ExternalReference r : externalRefs) {
            if (r.getType().equals(ExternalReference.Type.DISTRIBUTION)) {
                dist = r;
                break;
            }
        }
        if (dist == null) {
            dist = new ExternalReference();
            dist.setType(ExternalReference.Type.DISTRIBUTION);
            externalRefs.add(dist);
        }
        dist.setUrl(MRRC_URL);
        c.setExternalReferences(externalRefs);
    }

    private static Content getContent(PncArtifactBuildInfo info) {
        if (info == null) {
            return null;
        }
        if (info.getContent() == null || info.getContent().isEmpty()) {
            return null;
        }
        return info.getContent().get(0);
    }
}
