package io.quarkus.domino.manifest;

import org.cyclonedx.model.Bom;

public interface SbomTransformer {

    /**
     * Allows implementing SBOM transformations, such as adjusting and augmenting component metadata.
     * 
     * @param ctx transformation context that provides access to the original SBOM to be transformed
     * @return transformed SBOM instance
     */
    Bom transform(SbomTransformContext ctx);
}
