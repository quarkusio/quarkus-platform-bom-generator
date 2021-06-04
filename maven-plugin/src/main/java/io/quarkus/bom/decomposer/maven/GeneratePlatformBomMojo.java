package io.quarkus.bom.decomposer.maven;

import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.diff.BomDiff;
import io.quarkus.bom.diff.HtmlBomDiffReportGenerator;
import io.quarkus.bom.platform.PlatformBomComposer;
import io.quarkus.bom.platform.PlatformBomConfig;
import io.quarkus.bom.platform.PlatformBomUtils;
import io.quarkus.bom.platform.PlatformCatalogResolver;
import io.quarkus.bom.platform.ReportIndexPageGenerator;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

@Mojo(name = "generate-platform-bom", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.NONE)
public class GeneratePlatformBomMojo extends AbstractMojo {

    @Component
    private RepositorySystem repoSystem;

    @Component
    private RemoteRepositoryManager remoteRepoManager;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repos;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(defaultValue = "${skipPlatformBom}")
    protected boolean skip;

    @Parameter
    protected Set<String> enforcedDependencies = new HashSet<>(0);

    @Parameter
    protected Set<String> excludedDependencies = new HashSet<>(0);

    @Parameter
    protected Set<String> excludedGroups = new HashSet<>(0);

    @Parameter
    protected boolean enableNonMemberQuarkiverseExtensions;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    /**
     * Whether to record not found artifacts in a file that will serve as an artifact info cache for the subsequent builds.
     * If this option is enabled, the file be stored in <code>${basedir}/.quarkus-bom-generator/not-found-artifacts.txt</code>.
     */
    @Parameter(property = "recordNotFoundArtifacts")
    boolean recordNotFoundArtifacts;

    MavenArtifactResolver mavenResolver;
    ArtifactResolver artifactResolver;

    PlatformCatalogResolver catalogs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        try {
            doExecute();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze managed dependencies of " + project.getArtifact(), e);
        }
    }

    private void doExecute() throws Exception {
        //final MojoMessageWriter msgWriter = new MojoMessageWriter(getLog());

        final Path outputDir = Paths.get(project.getBuild().getDirectory()).resolve("boms");

        final PlatformBomConfig.Builder configBuilder = PlatformBomConfig.builder()
                .pomResolver(PomSource.of(project.getFile().toPath()));

        if (enforcedDependencies != null) {
            for (String enforced : enforcedDependencies) {
                final AppArtifactCoords coords = AppArtifact.fromString(enforced);
                configBuilder.enforce(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                        coords.getType(), coords.getVersion()));
            }
        }

        if (excludedDependencies != null) {
            for (String excluded : excludedDependencies) {
                configBuilder.exclude(AppArtifactKey.fromString(excluded));
            }
        }

        if (excludedGroups != null) {
            for (String excluded : excludedGroups) {
                configBuilder.excludeGroupId(excluded);
            }
        }

        final PlatformBomConfig config = configBuilder
                .enableNonMemberQuarkiverseExtensions(enableNonMemberQuarkiverseExtensions)
                .artifactResolver(artifactResolver())
                .build();

        try (ReportIndexPageGenerator index = new ReportIndexPageGenerator(outputDir.resolve("index.html"))) {
            final PlatformBomComposer bomComposer = new PlatformBomComposer(config, new MojoMessageWriter(getLog()));
            final DecomposedBom generatedBom = bomComposer.platformBom();

            final Path platformBomXml = outputDir.resolve(bomDirName(generatedBom.bomArtifact())).resolve("pom.xml");
            PlatformBomUtils.toPom(generatedBom, platformBomXml, project.getModel(), catalogResolver());
            project.setPomFile(platformBomXml.toFile());

            final Path generatedReleasesFile = outputDir.resolve(bomDirName(generatedBom.bomArtifact()))
                    .resolve("generated-releases.html");
            generateReleasesReport(generatedBom, generatedReleasesFile);
            index.universalBom(platformBomXml.toUri().toURL(), generatedBom, generatedReleasesFile);

            for (DecomposedBom importedBom : bomComposer.alignedMemberBoms()) {
                generateBomReports(bomComposer.originalMemberBom(importedBom.bomArtifact()), importedBom, null, outputDir,
                        index);
            }
        }
    }

    /**
     * 
     * @param originalBom original decomposed BOM
     * @param generatedBom generated decomposed BOM
     * @param outputDir BOM output directory
     * @param index report index page generator
     * @return generated BOM's pom.xml
     * @throws IOException in case of FS IO failure
     * @throws BomDecomposerException in case of BOM processing failure
     * @throws MojoExecutionException in case of maven resolver initialization failure
     */
    private Path generateBomReports(DecomposedBom originalBom, DecomposedBom generatedBom, Model baseModel,
            Path outputDir, ReportIndexPageGenerator index)
            throws IOException, BomDecomposerException, MojoExecutionException {
        outputDir = outputDir.resolve(bomDirName(generatedBom.bomArtifact()));
        final Path platformBomXml = outputDir.resolve("pom.xml");
        PlatformBomUtils.toPom(generatedBom, platformBomXml, baseModel, catalogResolver());
        generateBomReports(originalBom, generatedBom, outputDir, index, platformBomXml, artifactResolver());
        return platformBomXml;
    }

    public static void generateBomReports(DecomposedBom originalBom, DecomposedBom generatedBom, Path outputDir,
            ReportIndexPageGenerator index, final Path platformBomXml, ArtifactResolver resolver)
            throws BomDecomposerException {
        final BomDiff.Config config = BomDiff.config();
        config.resolver(resolver);
        if (originalBom.bomResolver() != null && originalBom.bomResolver().isResolved()) {
            config.compare(originalBom.bomResolver().pomPath());
        } else {
            config.compare(originalBom.bomArtifact());
        }
        final BomDiff bomDiff = config.to(platformBomXml);

        final Path diffFile = outputDir.resolve("diff.html");
        HtmlBomDiffReportGenerator.config(diffFile).report(bomDiff);

        final Path generatedReleasesFile = outputDir.resolve("generated-releases.html");
        generateReleasesReport(generatedBom, generatedReleasesFile);

        final Path originalReleasesFile = outputDir.resolve("original-releases.html");
        generateReleasesReport(originalBom, originalReleasesFile);

        index.bomReport(bomDiff.mainUrl(), bomDiff.toUrl(), generatedBom, originalReleasesFile, generatedReleasesFile,
                diffFile);
    }

    public static void generateReleasesReport(DecomposedBom originalBom, Path outputFile)
            throws BomDecomposerException {
        originalBom.visit(DecomposedBomHtmlReportGenerator.builder(outputFile)
                .skipOriginsWithSingleRelease().build());
    }

    private static String bomDirName(Artifact a) {
        return a.getGroupId() + "." + a.getArtifactId() + "-" + a.getVersion();
    }

    private PlatformCatalogResolver catalogResolver() throws MojoExecutionException {
        return catalogs == null ? catalogs = new PlatformCatalogResolver(mavenArtifactResolver()) : catalogs;
    }

    private ArtifactResolver artifactResolver() throws MojoExecutionException {
        if (artifactResolver == null) {
            final MavenArtifactResolver mavenResolver = mavenArtifactResolver();
            Path baseDir = null;
            if (recordNotFoundArtifacts) {
                LocalProject project = mavenResolver.getMavenContext().getCurrentProject();
                if (project != null) {
                    LocalProject parent;
                    while ((parent = project.getLocalParent()) != null) {
                        project = parent;
                    }
                }
                baseDir = project == null ? session.getTopLevelProject().getBasedir().toPath() : project.getDir();
            }
            artifactResolver = ArtifactResolverProvider.get(mavenResolver, baseDir);
        }
        return artifactResolver;
    }

    private MavenArtifactResolver mavenArtifactResolver() throws MojoExecutionException {
        try {
            return mavenResolver == null ? mavenResolver = MavenArtifactResolver.builder()
                    .setRepositorySystem(repoSystem)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .build() : mavenResolver;
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Maven artifact resolver", e);
        }
    }
}
