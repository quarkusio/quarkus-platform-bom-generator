package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.decomposer.maven.platformgen.MavenRepoZip;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

@Mojo(name = "generate-maven-repo-zip", threadSafe = true)
public class GenerateMavenRepoZipMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter
    GenerateMavenRepoZip generateMavenRepoZip;

    @Component
    QuarkusWorkspaceProvider bootstrapProvider;

    private MavenArtifactResolver resolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        MavenRepoZip.newGenerator()
                .setManagedDependencies(getManagedDependencies())
                .setConfig(generateMavenRepoZip)
                .setMavenArtifactResolver(getResolver())
                .setLog(new MojoMessageWriter(getLog()))
                .generate();
    }

    private List<Dependency> getManagedDependencies() throws MojoExecutionException {
        final String bomStr = generateMavenRepoZip == null ? null : generateMavenRepoZip.getBom();
        final List<Dependency> managedDeps;
        if (bomStr == null) {
            final List<org.apache.maven.model.Dependency> modelDeps = project.getDependencyManagement() == null
                    ? List.of()
                    : project.getDependencyManagement().getDependencies();
            managedDeps = modelDeps.stream().map(d -> {
                final DefaultArtifact a = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType(),
                        d.getVersion());
                return new Dependency(a, d.getScope(), d.isOptional());
            }).collect(Collectors.toList());
        } else {
            final ArtifactCoords coords = ArtifactCoords.fromString(bomStr);
            final Artifact a = new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), ArtifactCoords.TYPE_POM,
                    coords.getVersion());
            try {
                managedDeps = getResolver().resolveDescriptor(a).getManagedDependencies();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to resolve artifact descriptor for " + a, e);
            }
        }
        if (managedDeps.isEmpty()) {
            throw new MojoExecutionException("No managed dependencies were found in "
                    + (bomStr == null ? project.getGroupId() + ":" + project.getArtifactId() + "::pom:" + project.getVersion()
                            : bomStr));
        }
        return managedDeps;
    }

    private MavenArtifactResolver getResolver() throws MojoExecutionException {
        return resolver == null
                ? resolver = bootstrapProvider
                        .createArtifactResolver(BootstrapMavenContext.config().setWorkspaceDiscovery(true))
                : resolver;
    }
}
