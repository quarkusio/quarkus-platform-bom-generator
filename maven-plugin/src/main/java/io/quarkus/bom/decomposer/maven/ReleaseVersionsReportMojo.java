package io.quarkus.bom.decomposer.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposer.BomDecomposerConfig;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator.HtmlWriterBuilder;
import io.quarkus.bom.decomposer.DecomposedBomReleasesLogger;

@Mojo(name = "report-release-versions", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ReleaseVersionsReportMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(property = "bomHtmlReport", defaultValue = "true")
    protected boolean htmlReport = true;

    @Parameter(defaultValue = "${bomReportAll}")
    protected boolean reportAll;

    @Parameter(property = "bomReportLogging", defaultValue = "DEBUG")
    protected DecomposedBomReleasesLogger.Level reportLogging;

    @Parameter(property = "bomConflict", defaultValue = "WARN")
    protected DecomposedBomReleasesLogger.Level bomConflict;

    @Parameter(property = "bomResolvableConflict", defaultValue = "ERROR")
    protected DecomposedBomReleasesLogger.Level bomResolvableConflict;

    @Parameter(property = "bomSkipUpdates", defaultValue = "false")
    protected boolean skipUpdates;

    @Parameter(defaultValue = "${skipBomReport}")
    protected boolean skip;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if(skip) {
			return;
		}
		try {
			decompose();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to analyze managed dependencies of " + project.getArtifact(), e);
		}
	}

	private void decompose() throws Exception {
		final MojoMessageWriter msgWriter = new MojoMessageWriter(getLog());
		final BomDecomposerConfig config = BomDecomposer.config()
				.logger(msgWriter)
				.debug()
				.bomArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion());
		if(!skipUpdates) {
			config.checkForUpdates();
		}

		final DecomposedBom decomposedBom = config.decompose();

		if (htmlReport) {
			final HtmlWriterBuilder htmlWriter = DecomposedBomHtmlReportGenerator
					.builder("target/bom-report.html");
			if (!reportAll) {
				htmlWriter.skipOriginsWithSingleRelease();
			}
			decomposedBom.visit(htmlWriter.build());
		}

		if (reportLogging != null || bomConflict != null || bomResolvableConflict != null) {
			final DecomposedBomReleasesLogger.Config loggerConfig = DecomposedBomReleasesLogger.config(!reportAll);
			if(reportLogging != null) {
				loggerConfig.defaultLogLevel(reportLogging);
			}
			if(bomConflict != null) {
				loggerConfig.conflictLogLevel(bomConflict);
			}
			if(bomResolvableConflict != null) {
				loggerConfig.resolvableConflictLogLevel(bomResolvableConflict);
			}
			decomposedBom.visit(loggerConfig.logger(msgWriter).build());
		}
	}
}
