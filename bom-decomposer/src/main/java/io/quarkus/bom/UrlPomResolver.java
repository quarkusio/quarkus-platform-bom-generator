package io.quarkus.bom;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class UrlPomResolver implements PomResolver {

	private final URL baseUrl;
	private Artifact pomArtifact;
	private Model model;

	public UrlPomResolver(URL baseUrl) {
		this.baseUrl = baseUrl;
	}

	@Override
	public Path pomPath() {
		return Paths.get(baseUrl.getPath());
	}

	@Override
	public Model readLocalModel(Path pom) throws IOException {
		final URL url;
		String path = pom.toUri().getPath();
		if (baseUrl.getPath().equals(path)) {
			return model();
		}
		if (!path.endsWith(".xml")) {
			path += "/pom.xml";
		}
		url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(), path);
		return loadModel(url);
	}

	private Model model() {
		return model == null ? model = loadModel(baseUrl) : model;
	}

	private Model loadModel(final URL url) {
		try(InputStream stream = url.openStream()) {
			return ModelUtils.readModel(stream);
		} catch(IOException e) {
			return null;
		}
	}

	@Override
	public String source() {
		return baseUrl.toExternalForm();
	}

	@Override
	public Artifact pomArtifact() {
		if(pomArtifact == null) {
			final Model model = model();
			pomArtifact = new DefaultArtifact(ModelUtils.getGroupId(model), model.getArtifactId(), null, "pom",
					ModelUtils.getVersion(model));
		}
		return pomArtifact;
	}

	@Override
	public boolean isResolved() {
		return baseUrl.getProtocol().equals("file");
	}
}