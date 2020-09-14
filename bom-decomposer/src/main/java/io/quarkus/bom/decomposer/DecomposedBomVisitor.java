package io.quarkus.bom.decomposer;

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;

/**
 * Callback that receives events on detected releases and their content
 */
public interface DecomposedBomVisitor {

	/**
	 * Called only once at the beginning of the processing to communicate the BOM artifact
	 * that is being analyzed.
	 *
	 * @param bomArtifact  BOM that is being analyzed
	 */
	void enterBom(Artifact bomArtifact);

	/**
	 * Called for every new detected release origin.
	 * This callback method will be followed up by one or more
	 * {@link #enterReleaseVersion(ReleaseVersion, Collection)} invocations for the detected
	 * release versions from this origin.
	 *
	 * @param releaseOrigin  new detected release origin
	 */
	boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions);

	void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException;

	/**
	 * Called for every new release version.
	 *
	 * @param releaseVersion  release version
	 * @param artifacts  artifacts included in the release version
	 * @throws BomDecomposerException
	 */
	void visitProjectRelease(ProjectRelease release) throws BomDecomposerException;

	/**
	 * Called after the last processed release version in the BOM.
	 * @throws BomDecomposerException
	 */
	void leaveBom() throws BomDecomposerException;
}
