package io.quarkus.domino.manifest;

import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.test.repo.TestArtifactRepo;
import io.quarkus.domino.test.repo.TestProject;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import org.cyclonedx.model.Bom;

public class CyclicDependencyGraphTest extends ManifestingTestBase {

    @Override
    protected void initRepo(TestArtifactRepo repo) {
        var tcnativeProject = TestProject.of("io.netty", "1.0")
                .setRepoUrl("https://netty.io/tcnative")
                .setTag("1.0");
        tcnativeProject.createMainModule("netty-tcnative-boringssl-static")
                .addClassifier("linux-aarch_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-aarch_64", " 1.0"))
                .addClassifier("linux-x86_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", " 1.0"))
                .addClassifier("osx-aarch_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-aarch_64", " 1.0"))
                .addClassifier("osx-x86_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-x86_64", " 1.0"))
                .addClassifier("windows-x86_64")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "windows-x86_64", " 1.0"));
        repo.install(tcnativeProject);

        var appProject = TestProject.of("org.acme", "1.0")
                .setRepoUrl("https://acme.org/app")
                .setTag("1.0");
        appProject.createMainModule("acme-app")
                .addDependency(ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", "1.0"));
        repo.install(appProject);
    }

    @Override
    protected ProjectDependencyConfig initConfig(ProjectDependencyConfig.Mutable config) {
        return config.setProjectArtifacts(List.of(ArtifactCoords.jar("org.acme", "acme-app", "1.0")));
    }

    @Override
    protected void assertBom(Bom bom) {
        assertMainComponent(bom, ArtifactCoords.jar("org.acme", "acme-app", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", "1.0"));

        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-x86_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-aarch_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-aarch_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-x86_64", "1.0"),
                ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "windows-x86_64", "1.0"));

        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "linux-aarch_64", "1.0"));
        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-aarch_64", "1.0"));
        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "osx-x86_64", "1.0"));
        assertDependencies(bom, ArtifactCoords.jar("io.netty", "netty-tcnative-boringssl-static", "windows-x86_64", "1.0"));
    }
}
