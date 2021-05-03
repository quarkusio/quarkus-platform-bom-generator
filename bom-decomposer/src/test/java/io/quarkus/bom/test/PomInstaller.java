package io.quarkus.bom.test;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepositoryManager;

public class PomInstaller extends ModelComposer<PomInstaller> {

    public static PomInstaller newInstance() {
        return new PomInstaller();
    }

    public static PomInstaller forCoords(String coordsStr) {
        return new PomInstaller(coordsStr);
    }

    public static PomInstaller forGav(String groupId, String artifactId, String version) {
        return new PomInstaller().groupId(groupId).artifactId(artifactId).version(version);
    }

    private MavenArtifactResolver resolver;

    private PomInstaller(String coordsStr) {
        super(coordsStr);
    }

    private PomInstaller() {
        super();
    }

    public PomInstaller resolver(MavenArtifactResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public void install() {
        if (resolver == null) {
            throw new RuntimeException("Maven resolver hasn't been configured");
        }
        final Model model = model();
        final Artifact a = new DefaultArtifact(model.getGroupId(), model.getArtifactId(), null, "pom", model.getVersion());
        final Path target = initRepoPath(a).toPath();
        try {
            ModelUtils.persistModel(target, model);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist a POM for " + a + " at " + target, e);
        }
    }

    private File initRepoPath(Artifact a) {
        final LocalRepositoryManager localRepo = resolver.getSession().getLocalRepositoryManager();
        final File f = new File(localRepo.getRepository().getBasedir(), localRepo.getPathForLocalArtifact(a));
        f.getParentFile().mkdirs();
        return f;
    }
}
