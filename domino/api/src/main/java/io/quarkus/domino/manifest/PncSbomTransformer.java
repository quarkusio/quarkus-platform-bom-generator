package io.quarkus.domino.manifest;

import io.quarkus.domino.PncBuildInfoProvider;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.domino.manifest.PncArtifactBuildInfo.Content;
import java.util.ArrayList;
import java.util.List;
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

    private PncBuildInfoProvider pncInfoProvider = new PncBuildInfoProvider();

    @Override
    public Bom transform(SbomTransformContext ctx) {
        log.debug("Adding PNC build info to the manifest");
        final Bom bom = ctx.getOriginalBom();
        final Component product = bom.getMetadata() == null ? null : bom.getMetadata().getComponent();
        if (product != null) {
            addBuildId(product,
                    getContent(pncInfoProvider.getBuildInfo(product.getGroup(), product.getName(), product.getVersion())));
        }
        if (bom.getComponents() == null) {
            return bom;
        }
        for (Component c : bom.getComponents()) {
            addPncBuildInfo(c);
        }
        return bom;
    }

    private void addPncBuildInfo(Component c) {
        if (!RhVersionPattern.isRhVersion(c.getVersion())) {
            return;
        }
        final PncArtifactBuildInfo buildInfo = pncInfoProvider.getBuildInfo(c.getGroup(), c.getName(), c.getVersion());
        final Content content = getContent(buildInfo);
        if (content == null) {
            log.warn("PNC build info not found for " + c.getGroup() + ":" + c.getName() + ":" + c.getVersion());
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
            addBuildId(c, content);
        }
        addMrrc(c);
    }

    private void addBuildId(Component c, final Content content) {
        if (content == null || content.getBuild() == null) {
            log.warn("PNC build info not found for " + c.getGroup() + ":" + c.getName() + ":" + c.getVersion());
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

    static void addMrrc(Component c) {
        c.setPublisher(PUBLISHER);
        final List<ExternalReference> externalRefs = c.getExternalReferences() == null ? new ArrayList<>()
                : new ArrayList<>(c.getExternalReferences());
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
