package io.quarkus.bom.decomposer;

public interface DecomposedBomTransformer {

	DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom) throws BomDecomposerException;
}
