package io.quarkus.domino;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class ProjectDependencyResolverTest {

    @Test
    public void singleJarWithDependencies() throws Exception {

        var rc = getReleaseCollection(TestUtils.getResource("projects/multimodule-with-bom"),
                List.of(ArtifactCoords.jar("org.acme", "acme-library", "1.0")));

        assertThat(rc.isEmpty()).isFalse();
        assertThat(rc.size()).isEqualTo(1);
        var release = rc.iterator().next();
        assertThat(release.artifacts).hasSize(4);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-api", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-library", "1.0"));
    }

    @Test
    public void singleJarExcludeDependencyKey() throws Exception {

        var rc = getReleaseCollection(TestUtils.getResource("projects/multimodule-with-bom"),
                List.of(ArtifactCoords.jar("org.acme", "acme-library", "1.0")),
                config -> config.setExcludeKeys(List.of(ArtifactKey.ga("org.acme", "acme-api"))));

        assertThat(rc.isEmpty()).isFalse();
        assertThat(rc.size()).isEqualTo(1);
        var release = rc.iterator().next();
        assertThat(release.artifacts).hasSize(3);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-library", "1.0"));
    }

    @Test
    public void singleJarInclusionBeatsExclusion() throws Exception {

        var rc = getReleaseCollection(TestUtils.getResource("projects/multimodule-with-bom"),
                List.of(ArtifactCoords.jar("org.acme", "acme-library", "1.0")),
                config -> config.setExcludeKeys(List.of(ArtifactKey.ga("org.acme", "acme-api")))
                        .setIncludeKeys(List.of(ArtifactKey.ga("org.acme", "acme-api"))));

        assertThat(rc.isEmpty()).isFalse();
        assertThat(rc.size()).isEqualTo(1);
        var release = rc.iterator().next();
        assertThat(release.artifacts).hasSize(4);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-api", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-library", "1.0"));
    }

    @Test
    public void singleJarArtifactIncludingParentPomsAndBoms() throws Exception {

        var rc = getReleaseCollection(TestUtils.getResource("projects/single-jar-and-bom"),
                List.of(ArtifactCoords.jar("org.acme", "acme-library", "1.0")));

        assertThat(rc.isEmpty()).isFalse();
        assertThat(rc.size()).isEqualTo(1);
        var release = rc.iterator().next();
        assertThat(release.artifacts).hasSize(3);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-bom", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-library", "1.0"));
    }

    @Test
    public void singleJarArtifactExcludingParentPomsAndBoms() throws Exception {

        var rc = getReleaseCollection(TestUtils.getResource("projects/single-jar-and-bom"),
                List.of(ArtifactCoords.jar("org.acme", "acme-library", "1.0")),
                config -> config.setExcludeParentPoms(true));

        assertThat(rc.isEmpty()).isFalse();
        assertThat(rc.size()).isEqualTo(1);
        var release = rc.iterator().next();
        assertThat(release.artifacts).hasSize(1);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-library", "1.0"));
    }

    @Test
    public void singleJarArtifactIncludingParentPomsExcludingBoms() throws Exception {

        var rc = getReleaseCollection(TestUtils.getResource("projects/single-jar-and-bom"),
                List.of(ArtifactCoords.jar("org.acme", "acme-library", "1.0")),
                config -> config.setExcludeBomImports(true));

        assertThat(rc.isEmpty()).isFalse();
        assertThat(rc.size()).isEqualTo(1);
        var release = rc.iterator().next();
        assertThat(release.artifacts).hasSize(2);
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.pom("org.acme", "acme-parent", "1.0"));
        assertThat(release.getArtifacts()).containsKey(ArtifactCoords.jar("org.acme", "acme-library", "1.0"));
    }

    private static ReleaseCollection getReleaseCollection(Path projectDir, List<ArtifactCoords> projectArtifacts)
            throws BootstrapMavenException {
        return getReleaseCollection(projectDir, projectArtifacts, null);
    }

    private static ReleaseCollection getReleaseCollection(Path projectDir, List<ArtifactCoords> projectArtifacts,
            Function<ProjectDependencyConfig.Mutable, ProjectDependencyConfig.Mutable> configurator)
            throws BootstrapMavenException {
        final MavenArtifactResolver artifactResolver = MavenArtifactResolver.builder()
                .setOffline(true)
                .setCurrentProject(projectDir.toString())
                .build();
        var configBuilder = ProjectDependencyConfig.builder()
                .setProjectDir(projectDir)
                .setProjectArtifacts(projectArtifacts)
                .setWarnOnMissingScm(true)
                .setLegacyScmLocator(true);
        if (configurator != null) {
            configBuilder = configurator.apply(configBuilder);
        }
        var config = configBuilder.build();
        return ProjectDependencyResolver.builder()
                .setArtifactResolver(artifactResolver)
                .setDependencyConfig(config)
                .build()
                .getReleaseCollection();
    }
}
