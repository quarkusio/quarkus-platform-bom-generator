package io.quarkus.bom.decomposer;

import org.eclipse.aether.artifact.Artifact;

/**
 * Callback that receives events on detected releases and their content
 */
public interface DecomposedBomVisitor {

    /**
     * Called only once at the beginning of the processing to communicate the BOM artifact
     * that is being analyzed.
     *
     * @param bomArtifact BOM that is being analyzed
     */
    void enterBom(Artifact bomArtifact);

    /**
     * Called for every new detected release origin.
     * This callback method will be followed up by one or more
     * {@link #visitProjectRelease(ProjectRelease)} invocations for each detected
     * project release from this origin.
     *
     * @param releaseOrigin new detected release origin
     * @return whether to the detected project releases from this origin should be visited or not
     */
    boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions);

    void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException;

    /**
     * Called for every new release version.
     *
     * @param release project release
     * @throws BomDecomposerException in case of failure
     */
    void visitProjectRelease(ProjectRelease release) throws BomDecomposerException;

    /**
     * Called after the last processed release version in the BOM.
     * 
     * @throws BomDecomposerException in case of a failure
     */
    void leaveBom() throws BomDecomposerException;
}
