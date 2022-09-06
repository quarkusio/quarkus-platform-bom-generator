package io.quarkus.bom.decomposer;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class Util {

    private static final String HTTPS_GITHUB_COM = "https://github.com/";

    public static Artifact pom(Artifact artifact) {
        if (ArtifactCoords.TYPE_POM.equals(artifact.getExtension())) {
            return artifact;
        }
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), ArtifactCoords.DEFAULT_CLASSIFIER,
                ArtifactCoords.TYPE_POM, artifact.getVersion());
    }

    public static Model model(File pom) throws BomDecomposerException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(pom))) {
            return ModelUtils.readModel(is);
        } catch (Exception e) {
            throw new BomDecomposerException("Failed to parse POM " + pom, e);
        }
    }

    public static Artifact parentArtifact(Model model) {
        return model.getParent() == null ? null
                : new DefaultArtifact(model.getParent().getGroupId(), model.getParent().getArtifactId(),
                        ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_POM,
                        model.getParent().getVersion());
    }

    public static String getScmOrigin(Model model) {
        final Scm scm = model.getScm();
        if (scm == null) {
            return null;
        }
        if (scm.getConnection() != null) {
            String s = resolveModelValue(model, scm.getConnection());
            s = scmToHttps(s);
            return s;
        }
        String url = resolveModelValue(model, model.getUrl());
        if (url != null && url.startsWith("https://github.com/")) {
            return scmToHttps(url);
        }
        url = resolveModelValue(model, scm.getUrl());
        if (url != null && url.startsWith("https://github.com/")) {
            return scmToHttps(url);
        }
        return null;
    }

    private static String scmToHttps(String s) {
        s = s.replace("scm:", "");
        s = s.replace("git:", "");
        s = s.replace("git@", "");
        s = s.replace("ssh:", "");
        s = s.replace("svn:", "");
        s = s.replace(".git", "");
        if (s.startsWith("http://")) {
            s = s.replace("http://", "https://");
        } else if (!s.startsWith("https://")) {
            s = s.replace(':', '/');
            if (s.startsWith("github.com:")) {
                s = s.replace(':', '/');
            }
            if (s.startsWith("//")) {
                s = "https:" + s;
            } else {
                s = "https://" + s;
            }
        }
        if (s.startsWith(HTTPS_GITHUB_COM)) {
            var tmp = s.substring(HTTPS_GITHUB_COM.length());
            final String[] parts = tmp.split("/");
            if (parts.length > 2) {
                s = HTTPS_GITHUB_COM + parts[0] + "/" + parts[1];
            }
        }

        return s;
    }

    public static String getScmTag(Model model) {
        return model.getScm() == null ? null : resolveModelValue(model, model.getScm().getTag());
    }

    private static String resolveModelValue(Model model, String value) {
        return value == null ? null : value.contains("${") ? substituteProperties(value, model) : value;
    }

    private static String substituteProperties(String str, Model model) {
        final Properties props = model.getProperties();
        Map<String, String> map = new HashMap<>(props.size());
        for (Map.Entry<?, ?> prop : props.entrySet()) {
            map.put(prop.getKey().toString(), prop.getValue().toString());
        }
        map.put("project.version", ModelUtils.getVersion(model));
        return new StringSubstitutor(map).replace(str);
    }
}
