package io.quarkus.domino.recipes.scm;

import io.quarkus.domino.recipes.GAV;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Scm;
import org.jboss.logging.Logger;

public abstract class AbstractPomScmLocator implements ScmLocator {

    private static final Logger log = Logger.getLogger(AbstractPomScmLocator.class);

    private static final String HTTPS_GITHUB_COM = "https://github.com/";

    @Override
    public TagInfo resolveTagInfo(GAV toBuild) {
        try (PomClient client = createPomClient()) {
            Optional<Model> currentPom = client.getPom(toBuild.getGroupId(), toBuild.getArtifactId(), toBuild.getVersion());
            while (currentPom.isPresent()) {
                var origin = getScmOrigin(currentPom.get(), toBuild.getVersion());
                if (origin != null) {
                    return origin;
                }
                Parent p = currentPom.get().getParent();
                if (p == null) {
                    return null;
                }
                if (!Objects.equals(p.getGroupId(), toBuild.getGroupId())) {
                    return null;
                }
                currentPom = client.getPom(p.getGroupId(), p.getArtifactId(), p.getVersion());
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to get pom for %s:%s:%s", toBuild.getGroupId(), toBuild.getArtifactId(),
                    toBuild.getVersion());
            return null;
        }
        return null;
    }

    protected abstract PomClient createPomClient();

    public static TagInfo getScmOrigin(Model model, String version) {
        final Scm scm = model.getScm();
        if (scm == null) {
            return null;
        }
        if (scm.getConnection() != null && !scm.getConnection().isEmpty()) {
            return toTagInfo(scm, resolveModelValue(model, scm.getConnection(), version));
        }
        String url = resolveModelValue(model, model.getUrl(), version);
        if (url != null && url.startsWith("https://github.com/")) {
            return toTagInfo(scm, url);
        }
        url = resolveModelValue(model, scm.getUrl(), version);
        if (url != null && url.startsWith("https://github.com/")) {
            return toTagInfo(scm, url);
        }
        return null;
    }

    private static TagInfo toTagInfo(final Scm scm, String url) {
        return new TagInfo(new RepositoryInfo("git", scmToHttps(url)), scm.getTag(), null);
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

    private static String resolveModelValue(Model model, String value, String version) {
        return value == null ? null : value.contains("${") ? substituteProperties(value, model, version) : value;
    }

    private static String substituteProperties(String str, Model model, String version) {
        final Properties props = model.getProperties();
        Map<String, String> map = new HashMap<>(props.size());
        for (Map.Entry<?, ?> prop : props.entrySet()) {
            map.put(prop.getKey().toString(), prop.getValue().toString());
        }
        map.put("project.version", version);
        map.put("project.artifactId", model.getArtifactId());
        return new StringSubstitutor(map).replace(str);
    }

    public interface PomClient extends AutoCloseable {

        Optional<Model> getPom(String group, String artifact, String version);

        void close();
    }
}
