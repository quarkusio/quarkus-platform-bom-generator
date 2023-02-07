package io.quarkus.domino.manifest;

import org.cyclonedx.model.Bom;

public interface SbomTransformContext {

    /**
     * The original BOM instance to be transformed
     * 
     * @return the original BOM instance to be transformed
     */
    Bom getOriginalBom();
}
