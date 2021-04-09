package io.quarkus.bom.decomposer;

public interface DecomposedBomTransformer {

    DecomposedBom transform(DecomposedBom decomposedBom) throws BomDecomposerException;
}
