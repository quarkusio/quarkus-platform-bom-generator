package io.quarkus.bom.decomposer;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.eclipse.aether.artifact.Artifact;

public class PomUtils {

    /**
     * Converts decomposed BOM to a POM Model
     * 
     * @param decomposed decomposed BOM
     * @return POM Model
     */
    public static Model toModel(DecomposedBom decomposed) {
        return toModel(decomposed, null);
    }

    /**
     * Converts decomposed BOM to a POM Model copying developer info, SCM, etc
     * from the base Model
     * 
     * @param decomposed decomposed BOM
     * @param baseModel base Model info
     * @return POM Model
     */
    public static Model toModel(DecomposedBom decomposed, Model baseModel) {

        final DependencyManagement dm = new DependencyManagement();

        final Map<String, Artifact> artifacts = new HashMap<>();
        for (ProjectRelease release : decomposed.releases()) {
            for (ProjectDependency dep : release.dependencies()) {
                artifacts.put(dep.key().toString(), dep.artifact);
            }
        }
        final List<String> keys = new ArrayList<>(artifacts.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            final Artifact a = artifacts.get(key);
            final Dependency dep = new Dependency();
            dep.setGroupId(a.getGroupId());
            dep.setArtifactId(a.getArtifactId());
            if (!a.getClassifier().isEmpty()) {
                dep.setClassifier(a.getClassifier());
            }
            if (!"jar".equals(a.getExtension())) {
                dep.setType(a.getExtension());
            }
            dep.setVersion(a.getVersion());
            dm.addDependency(dep);
        }

        final Model model = new Model();
        model.setModelVersion(modelVersion(baseModel));
        model.setGroupId(decomposed.bomArtifact().getGroupId());
        model.setArtifactId(decomposed.bomArtifact().getArtifactId());
        model.setVersion(decomposed.bomArtifact().getVersion());
        model.setPackaging("pom");
        model.setName(name(baseModel));
        model.setDescription(description(baseModel));
        model.setUrl(url(baseModel));
        model.setDevelopers(developers(baseModel));
        model.setLicenses(licenses(baseModel));
        model.setDependencyManagement(dm);
        model.setScm(scm(baseModel));
        model.setCiManagement(ciManagement(baseModel));
        model.setIssueManagement(issueManagement(baseModel));
        model.setDistributionManagement(distributionManagement(baseModel));
        return model;
    }

    /**
     * Persists decomposed BOM to a pom.xml file
     * 
     * @param decomposed decomposed BOM
     * @param file target file
     * @throws IOException in case of a failure
     */
    public static void toPom(DecomposedBom decomposed, Path file) throws IOException {
        toPom(decomposed, file, null);
    }

    /**
     * Persists decomposed BOM to a pom.xml filling in developer, SCM and other info from the base model
     * 
     * @param decomposed decomposed BOM
     * @param file target file
     * @param baseModel base model info
     * @throws IOException in case of a failure
     */
    public static void toPom(DecomposedBom decomposed, Path file, Model baseModel) throws IOException {
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
        ModelUtils.persistModel(file, toModel(decomposed, baseModel));
    }

    private static String modelVersion(Model base) {
        return base == null ? "4.0.0" : base.getModelVersion();
    }

    private static String name(Model base) {
        return base == null ? "Generated Quarkus platform BOM" : base.getName();
    }

    private static String description(Model base) {
        return base == null ? "Generated Quarkus platform BOM" : base.getDescription();
    }

    private static String url(Model base) {
        return base == null ? null : base.getUrl();
    }

    private static List<Developer> developers(Model base) {
        return base == null ? Collections.emptyList() : base.getDevelopers();
    }

    private static List<License> licenses(Model base) {
        return base == null ? Collections.emptyList() : base.getLicenses();
    }

    private static Scm scm(Model base) {
        return base == null ? null : base.getScm();
    }

    private static CiManagement ciManagement(Model base) {
        return base == null ? null : base.getCiManagement();
    }

    private static IssueManagement issueManagement(Model base) {
        return base == null ? null : base.getIssueManagement();
    }

    private static DistributionManagement distributionManagement(Model base) {
        return base == null ? null : base.getDistributionManagement();
    }
}
