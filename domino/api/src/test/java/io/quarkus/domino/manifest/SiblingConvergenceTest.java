package io.quarkus.domino.manifest;

import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.domino.test.repo.TestArtifactRepo;
import io.quarkus.domino.test.repo.TestModule;
import io.quarkus.domino.test.repo.TestProject;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import org.cyclonedx.model.Bom;

public class SiblingConvergenceTest extends ManifestingTestBase {
    @Override
    protected void initRepo(TestArtifactRepo repo) {

        var projectA1 = TestProject.of("org.project-a", "0.1")
                .setRepoUrl("https://project-a.org/code")
                .setTag("0.1");
        var libA1 = projectA1.createMainModule("lib-a")
                .addDependency(installLibX(repo, "0.1"));
        repo.install(projectA1);

        var projectB1 = TestProject.of("org.project-b", "0.1")
                .setRepoUrl("https://project-b.org/code")
                .setTag("0.1");
        var libB1 = projectB1.createMainModule("lib-b")
                .addDependency(installLibX(repo, "0.2"));
        repo.install(projectB1);

        var projectC1 = TestProject.of("org.project-c", "0.1")
                .setRepoUrl("https://project-c.org/code")
                .setTag("0.1");
        var libC1 = projectC1.createMainModule("lib-c")
                .addDependency(installLibX(repo, "0.3"));
        repo.install(projectC1);

        var projectD1 = TestProject.of("org.project-d", "0.1")
                .setRepoUrl("https://project-d.org/code")
                .setTag("0.1");
        var libD1 = projectD1.createMainModule("lib-d")
                .addDependency(installLibX(repo, "0.4"));
        repo.install(projectD1);

        var projectE1 = TestProject.of("org.project-e", "0.1")
                .setRepoUrl("https://project-e.org/code")
                .setTag("0.1");
        var libE1 = projectE1.createMainModule("lib-e")
                .addDependency(installLibX(repo, "0.5"));
        repo.install(projectE1);

        var projectF1 = TestProject.of("org.project-f", "0.1")
                .setRepoUrl("https://project-f.org/code")
                .setTag("0.1");
        var libF1 = projectF1.createMainModule("lib-f")
                .addDependency(installLibX(repo, "0.6"));
        repo.install(projectF1);

        var projectG1 = TestProject.of("org.project-g", "0.1")
                .setRepoUrl("https://project-g.org/code")
                .setTag("0.1");
        var libG1 = projectG1.createMainModule("lib-g")
                .addDependency(installLibX(repo, "0.7"));
        repo.install(projectG1);

        var projectH1 = TestProject.of("org.project-h", "0.1")
                .setRepoUrl("https://project-h.org/code")
                .setTag("0.1");
        var libH1 = projectH1.createMainModule("lib-h")
                .addDependency(installLibX(repo, "0.8"));
        repo.install(projectH1);

        var projectI1 = TestProject.of("org.project-i", "0.1")
                .setRepoUrl("https://project-i.org/code")
                .setTag("0.1");
        var libI1 = projectI1.createMainModule("lib-i")
                .addDependency(installLibX(repo, "0.9"));
        repo.install(projectI1);

        var projectJ1 = TestProject.of("org.project-j", "0.1")
                .setRepoUrl("https://project-j.org/code")
                .setTag("0.1");
        var libJ1 = projectJ1.createMainModule("lib-j")
                .addDependency(installLibX(repo, "0.10"));
        repo.install(projectJ1);

        var projectK1 = TestProject.of("org.project-k", "0.1")
                .setRepoUrl("https://project-k.org/code")
                .setTag("0.1");
        var libK1 = projectK1.createMainModule("lib-k")
                .addDependency(installLibX(repo, "0.11"));
        repo.install(projectK1);

        var projectL1 = TestProject.of("org.project-l", "0.1")
                .setRepoUrl("https://project-l.org/code")
                .setTag("0.1");
        var libL1 = projectL1.createMainModule("lib-l")
                .addDependency(installLibX(repo, "0.12"));
        repo.install(projectL1);

        var projectM1 = TestProject.of("org.project-m", "0.1")
                .setRepoUrl("https://project-m.org/code")
                .setTag("0.1");
        var libM1 = projectM1.createMainModule("lib-m")
                .addDependency(installLibX(repo, "0.13"));
        repo.install(projectM1);

        var projectN1 = TestProject.of("org.project-n", "0.1")
                .setRepoUrl("https://project-n.org/code")
                .setTag("0.1");
        var libN1 = projectN1.createMainModule("lib-n")
                .addDependency(installLibX(repo, "0.14"));
        repo.install(projectN1);

        var projectO1 = TestProject.of("org.project-o", "0.1")
                .setRepoUrl("https://project-o.org/code")
                .setTag("0.1");
        var libO1 = projectO1.createMainModule("lib-o")
                .addDependency(installLibX(repo, "0.15"));
        repo.install(projectO1);

        var projectP1 = TestProject.of("org.project-p", "0.1")
                .setRepoUrl("https://project-p.org/code")
                .setTag("0.1");
        var libP1 = projectP1.createMainModule("lib-p")
                .addDependency(installLibX(repo, "0.16"));
        repo.install(projectP1);

        var projectQ1 = TestProject.of("org.project-q", "0.1")
                .setRepoUrl("https://project-q.org/code")
                .setTag("0.1");
        var libQ1 = projectQ1.createMainModule("lib-q")
                .addDependency(installLibX(repo, "0.17"));
        repo.install(projectQ1);

        var projectR1 = TestProject.of("org.project-r", "0.1")
                .setRepoUrl("https://project-r.org/code")
                .setTag("0.1");
        var libR1 = projectR1.createMainModule("lib-r")
                .addDependency(installLibX(repo, "0.18"));
        repo.install(projectR1);

        var projectS1 = TestProject.of("org.project-s", "0.1")
                .setRepoUrl("https://project-s.org/code")
                .setTag("0.1");
        var libS1 = projectS1.createMainModule("lib-s")
                .addDependency(installLibX(repo, "0.19"));
        repo.install(projectS1);

        var projectT1 = TestProject.of("org.project-t", "0.1")
                .setRepoUrl("https://project-t.org/code")
                .setTag("0.1");
        var libT1 = projectT1.createMainModule("lib-t")
                .addDependency(installLibX(repo, "0.20"));
        repo.install(projectT1);

        var projectU1 = TestProject.of("org.project-u", "0.1")
                .setRepoUrl("https://project-u.org/code")
                .setTag("0.1");
        var libU1 = projectU1.createMainModule("lib-u")
                .addDependency(installLibX(repo, "0.21"));
        repo.install(projectU1);

        var projectV1 = TestProject.of("org.project-v", "0.1")
                .setRepoUrl("https://project-v.org/code")
                .setTag("0.1");
        var libV1 = projectV1.createMainModule("lib-v")
                .addDependency(installLibX(repo, "0.22"));
        repo.install(projectV1);

        var projectW1 = TestProject.of("org.project-w", "0.1")
                .setRepoUrl("https://project-w.org/code")
                .setTag("0.1");
        var libW1 = projectW1.createMainModule("lib-w")
                .addDependency(installLibX(repo, "0.23"));
        repo.install(projectW1);

        var appProject = TestProject.of("org.acme", "1.0")
                .setRepoUrl("https://acme.org/app")
                .setTag("1.0");
        appProject.createMainModule("acme-app")
                .addDependency(libA1)
                .addDependency(libB1)
                .addDependency(libC1)
                .addDependency(libD1)
                .addDependency(libE1)
                .addDependency(libF1)
                .addDependency(libG1)
                .addDependency(libH1)
                .addDependency(libI1)
                .addDependency(libJ1)
                .addDependency(libK1)
                .addDependency(libL1)
                .addDependency(libM1)
                .addDependency(libN1)
                .addDependency(libO1)
                .addDependency(libP1)
                .addDependency(libQ1)
                .addDependency(libR1)
                .addDependency(libS1)
                .addDependency(libT1)
                .addDependency(libU1)
                .addDependency(libV1)
                .addDependency(libW1);
        repo.install(appProject);

    }

    private TestModule installLibX(TestArtifactRepo repo, String version) {
        var projectY1 = TestProject.of("org.project-y", version)
                .setRepoUrl("https://project-y.org/code")
                .setTag(version);
        var libY1 = projectY1.createMainModule("lib-y");
        repo.install(projectY1);
        var projectX1 = TestProject.of("org.project-x", version)
                .setRepoUrl("https://project-x.org/code")
                .setTag(version);
        var libX1 = projectX1.createMainModule("lib-x")
                .addDependency(libY1);
        repo.install(projectX1);
        return libX1;
    }

    @Override
    protected ProjectDependencyConfig initConfig(ProjectDependencyConfig.Mutable config) {
        return config.setProjectArtifacts(List.of(ArtifactCoords.jar("org.acme", "acme-app", "1.0")));
    }

    @Override
    protected void assertBom(Bom bom) {
        assertMainComponent(bom, ArtifactCoords.jar("org.acme", "acme-app", "1.0"),
                ArtifactCoords.jar("org.project-a", "lib-a", "0.1"),
                ArtifactCoords.jar("org.project-b", "lib-b", "0.1"),
                ArtifactCoords.jar("org.project-c", "lib-c", "0.1"),
                ArtifactCoords.jar("org.project-d", "lib-d", "0.1"),
                ArtifactCoords.jar("org.project-e", "lib-e", "0.1"),
                ArtifactCoords.jar("org.project-f", "lib-f", "0.1"),
                ArtifactCoords.jar("org.project-g", "lib-g", "0.1"),
                ArtifactCoords.jar("org.project-h", "lib-h", "0.1"),
                ArtifactCoords.jar("org.project-i", "lib-i", "0.1"),
                ArtifactCoords.jar("org.project-j", "lib-j", "0.1"),
                ArtifactCoords.jar("org.project-k", "lib-k", "0.1"),
                ArtifactCoords.jar("org.project-l", "lib-l", "0.1"),
                ArtifactCoords.jar("org.project-m", "lib-m", "0.1"),
                ArtifactCoords.jar("org.project-n", "lib-n", "0.1"),
                ArtifactCoords.jar("org.project-o", "lib-o", "0.1"),
                ArtifactCoords.jar("org.project-p", "lib-p", "0.1"),
                ArtifactCoords.jar("org.project-q", "lib-q", "0.1"),
                ArtifactCoords.jar("org.project-r", "lib-r", "0.1"),
                ArtifactCoords.jar("org.project-s", "lib-s", "0.1"),
                ArtifactCoords.jar("org.project-t", "lib-t", "0.1"),
                ArtifactCoords.jar("org.project-u", "lib-u", "0.1"),
                ArtifactCoords.jar("org.project-v", "lib-v", "0.1"),
                ArtifactCoords.jar("org.project-w", "lib-w", "0.1"));

        assertDependencies(bom, ArtifactCoords.jar("org.project-a", "lib-a", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-b", "lib-b", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-c", "lib-c", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-d", "lib-d", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-e", "lib-e", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-f", "lib-f", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-g", "lib-g", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-h", "lib-h", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-i", "lib-i", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-j", "lib-j", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-k", "lib-k", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-l", "lib-l", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-m", "lib-m", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-n", "lib-n", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-o", "lib-o", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-p", "lib-p", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-q", "lib-q", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-r", "lib-r", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-s", "lib-s", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-t", "lib-t", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-u", "lib-u", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-v", "lib-v", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));
        assertDependencies(bom, ArtifactCoords.jar("org.project-w", "lib-w", "0.1"),
                ArtifactCoords.jar("org.project-x", "lib-x", "0.1"));

        assertDependencies(bom, ArtifactCoords.jar("org.project-x", "lib-x", "0.1"),
                ArtifactCoords.jar("org.project-y", "lib-y", "0.1"));
    }
}
