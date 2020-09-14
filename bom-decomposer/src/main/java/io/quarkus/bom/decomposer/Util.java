package io.quarkus.bom.decomposer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class Util {

	public static AppArtifactKey key(Artifact artifact) {
		return new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension());
	}

	public static Artifact pom(Artifact artifact) {
		if("pom".equals(artifact.getExtension())) {
			return artifact;
		}
		return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "", "pom", artifact.getVersion());
	}

	public static Model model(File pom) throws BomDecomposerException {
		try (InputStream is = new BufferedInputStream(new FileInputStream(pom))) {
			return ModelUtils.readModel(is);
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to parse POM " + pom, e);
		}
	}

	public static Artifact parentArtifact(Model model) {
		return model.getParent() == null ? null
				: new DefaultArtifact(model.getParent().getGroupId(), model.getParent().getArtifactId(), "", "pom",
						model.getParent().getVersion());
	}

	public static String getScmOrigin(Model model) {
		final Scm scm = model.getScm();
		if(scm == null) {
			return null;
		}
		if(scm.getConnection() != null) {
			return resolveModelValue(model, scm.getConnection());
		}
		final String url = resolveModelValue(model, model.getUrl());
		if(url != null && url.startsWith("https://github.com/")) {
			return url;
		}
		return null;
	}

	public static String getScmTag(Model model) {
		return model.getScm() == null ? null : resolveModelValue(model, model.getScm().getTag());
	}

	private static String resolveModelValue(Model model, String value) {
		return value == null ? null : value.contains("${") ? substituteProperties(value, model) : value;
	}

	private static String substituteProperties(String str, Model model) {
		final Properties props = model.getProperties();
		Map<String, String> map = new HashMap<>(props.size());
		for(Map.Entry<?, ?> prop : props.entrySet()) {
			map.put(prop.getKey().toString(), prop.getValue().toString());
		}
		map.put("project.version", ModelUtils.getVersion(model));
		return new StringSubstitutor(map).replace(str);
	}
}
