package io.quarkus.bom;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.aether.artifact.Artifact;

public class PomSource {

    public static PomResolver of(Path pomFile) {
        if (!Files.exists(pomFile)) {
            throw new IllegalArgumentException("Path does not exist " + pomFile);
        }
        return new FsPomResolver(pomFile);
    }

    public static PomResolver of(URL pomFile) {
        return new UrlPomResolver(pomFile);
    }

    public static PomResolver githubPom(String repoPom) {
        final StringBuilder buf = new StringBuilder();
        buf.append("https://raw.githubusercontent.com");
        if (repoPom.charAt(0) != '/') {
            buf.append("/");
        }
        buf.append(repoPom);
        final URL url;
        try {
            url = new URL(buf.toString());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Failed to create a github URL for " + repoPom, e);
        }
        return of(url);
    }

    public static PomResolver of(Artifact artifact) {
        return new RepositoryPomResolver(artifact);
    }
}
