package io.quarkus.bom.decomposer.maven.platformgen;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

@Mojo(name = "attach-maven-plugin", threadSafe = true)
public class AttachPluginMojo extends AbstractMojo {

    @Parameter(required = true)
    String originalPluginCoords;

    @Parameter(required = true)
    String targetPluginCoords;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repoSystem;

    @Component
    private RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repos;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
    private List<RemoteRepository> pluginRepos;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final ArtifactCoords originalCoords = ArtifactCoords.fromString(originalPluginCoords);
        final ArtifactCoords targetCoords = ArtifactCoords.fromString(targetPluginCoords);

        final Path targetDir = Paths.get(project.getBuild().getDirectory());
        if (!Files.exists(targetDir)) {
            try {
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to create directory " + targetDir, e);
            }
        }

        final Artifact mainArtifact = new DefaultArtifact(originalCoords.getGroupId(), originalCoords.getArtifactId(), null,
                "jar", originalCoords.getVersion());
        final Path mainJar = resolve(mainArtifact);
        final Path classesDir = targetDir.resolve("classes");
        try {
            ZipUtils.unzip(mainJar, classesDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to unpack " + mainJar + " to " + targetDir.resolve("classes"), e);
        }
        // remove the original maven files
        IoUtils.recursiveDelete(classesDir.resolve("META-INF").resolve("maven"));

        final Path originalPom = resolve(new DefaultArtifact(originalCoords.getGroupId(), originalCoords.getArtifactId(), null,
                "pom", originalCoords.getVersion()));
        final Model generatedModel = project.getOriginalModel().clone();
        generatedModel.setGroupId(targetCoords.getGroupId());
        generatedModel.setArtifactId(targetCoords.getArtifactId());
        generatedModel.setVersion(targetCoords.getVersion());
        try {
            generatedModel.setBuild(ModelUtils.readModel(originalPom).getBuild());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + originalPom, e);
        }

        final Set<ArtifactKey> managedKeys = getManagedKeys();
        final ArtifactDescriptorResult descr = describe(mainArtifact);
        for (Dependency d : descr.getDependencies()) {
            if ("test".equals(d.getScope())) {
                continue;
            }
            final org.apache.maven.model.Dependency modelDep = new org.apache.maven.model.Dependency();
            final Artifact a = d.getArtifact();
            modelDep.setGroupId(a.getGroupId());
            modelDep.setArtifactId(a.getArtifactId());
            if (!a.getClassifier().isEmpty()) {
                modelDep.setClassifier(a.getClassifier());
            }
            if (!a.getExtension().isEmpty() && !"jar".equals(a.getExtension())) {
                modelDep.setType(a.getExtension());
            }
            if (!managedKeys.contains(new ArtifactKey(modelDep.getGroupId(), modelDep.getArtifactId(), modelDep.getClassifier(),
                    modelDep.getType()))) {
                modelDep.setVersion(a.getVersion());
            }
            if (!d.getScope().isEmpty() && !"compile".equals(d.getScope())) {
                modelDep.setScope(d.getScope());
            }
            if (d.isOptional()) {
                modelDep.setOptional(true);
            }
            for (Exclusion e : d.getExclusions()) {
                final org.apache.maven.model.Exclusion modelE = new org.apache.maven.model.Exclusion();
                modelE.setGroupId(e.getGroupId());
                modelE.setArtifactId(e.getArtifactId());
                modelDep.addExclusion(modelE);
            }
            generatedModel.addDependency(modelDep);
        }

        final Path generatedPom = targetDir.resolve(targetCoords.getArtifactId() + "-" + targetCoords.getVersion() + ".pom");
        try {
            ModelUtils.persistModel(generatedPom, generatedModel);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to persiste POM at " + generatedPom, e);
        }

        project.setPomFile(generatedPom.toFile());
    }

    private ArtifactDescriptorResult describe(final Artifact a) throws MojoExecutionException {
        try {
            return repoSystem.readArtifactDescriptor(repoSession,
                    new ArtifactDescriptorRequest().setArtifact(a).setRepositories(pluginRepos));
        } catch (ArtifactDescriptorException e) {
            throw new MojoExecutionException("Failed to describe " + a, e);
        }
    }

    private Path resolve(final Artifact pluginArtifact) throws MojoExecutionException {
        try {
            return repoSystem
                    .resolveArtifact(repoSession,
                            new ArtifactRequest().setArtifact(pluginArtifact).setRepositories(pluginRepos))
                    .getArtifact().getFile().toPath();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve artifact " + pluginArtifact, e);
        }
    }

    private Set<ArtifactKey> getManagedKeys() {
        final DependencyManagement dm = project.getDependencyManagement();
        if (dm == null) {
            return Collections.emptySet();
        }
        final List<org.apache.maven.model.Dependency> deps = dm.getDependencies();
        if (deps.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<ArtifactKey> keys = new HashSet<>(deps.size());
        for (org.apache.maven.model.Dependency d : deps) {
            keys.add(new ArtifactKey(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType()));
        }
        return keys;
    }
}
