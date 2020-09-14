package io.quarkus.bom.decomposer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.aether.artifact.Artifact;

public abstract class DecomposedBomReportFileWriter extends FileReportWriter implements DecomposedBomVisitor {

	public DecomposedBomReportFileWriter(String name) {
		super(name);
	}

	public DecomposedBomReportFileWriter(Path p) {
		super(p);
	}

	@Override
	public void enterBom(Artifact bomArtifact) {
		try {
			writeStartBom(writer(), bomArtifact);
		} catch (Exception e) {
			close();
			throw new IllegalStateException("Failed to init " + reportFile + " writer", e);
		}
	}

	protected abstract void writeStartBom(BufferedWriter writer, Artifact bomArtifact) throws IOException;

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		try {
			return writeStartReleaseOrigin(writer(), releaseOrigin, versions);
		} catch(Exception e) {
			close();
			throw new IllegalStateException("Failed to write release origin " + releaseOrigin + " to " + reportFile, e);
		}
	}

	protected abstract boolean writeStartReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin, int versions) throws IOException;

	@Override
	public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) {
		try {
			writeEndReleaseOrigin(writer(), releaseOrigin);
		} catch(Exception e) {
			close();
			throw new IllegalStateException("Failed to write release origin " + releaseOrigin + " to " + reportFile, e);
		}
	}

	protected abstract void writeEndReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException;

	@Override
	public void visitProjectRelease(ProjectRelease release) {
		try {
			writeProjectRelease(writer(), release);
		} catch (Exception e) {
			close();
			throw new IllegalStateException("Failed to write release " + release.id() + " to " + reportFile, e);
		}
	}

	protected abstract void writeProjectRelease(BufferedWriter writer, ProjectRelease release) throws IOException;

	@Override
	public void leaveBom() {
		try {
			writeEndBom(writer());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to write conclusion to " + reportFile, e);
		} finally {
			close();
		}
	}

	protected abstract void writeEndBom(BufferedWriter writer) throws IOException;
}
