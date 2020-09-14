package io.quarkus.bom.decomposer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class PomUtils {

	public static Model toModel(DecomposedBom decomposed) {
		final DependencyManagement dm = new DependencyManagement();

		final Map<String, Artifact> artifacts = new HashMap<>();
		for(ProjectRelease release : decomposed.releases()) {
			for(ProjectDependency dep : release.dependencies()) {
				artifacts.put(dep.key().toString(), dep.artifact);
			}
		}
		final List<String> keys = new ArrayList<>(artifacts.keySet());
		Collections.sort(keys);
		for(String key : keys) {
			final Artifact a = artifacts.get(key);
			final Dependency dep = new Dependency();
			dep.setGroupId(a.getGroupId());
			dep.setArtifactId(a.getArtifactId());
			if(!a.getClassifier().isEmpty()) {
				dep.setClassifier(a.getClassifier());
			}
			if(!"jar".equals(a.getExtension())) {
				dep.setType(a.getExtension());
			}
			dep.setVersion(a.getVersion());
			dm.addDependency(dep);
		}

		final Model model = new Model();
		model.setModelVersion("4.0.0");
		model.setGroupId(decomposed.bomArtifact().getGroupId());
		model.setArtifactId(decomposed.bomArtifact().getArtifactId());
		model.setVersion(decomposed.bomArtifact().getVersion());
		model.setPackaging("pom");
		model.setName("Quarkus platform BOM");
		model.setDependencyManagement(dm);
		return model;
	}

	public static void toPom(DecomposedBom decomposed, Path file) throws IOException {
		if(!Files.exists(file.getParent())) {
			Files.createDirectories(file.getParent());
		}
		ModelUtils.persistModel(file, toModel(decomposed));
	}
}
