package com.redhat.hacbs.recipes.scm;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.text.StringSubstitutor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.jboss.logging.Logger;

public class PomScmLocator extends AbstractPomScmLocator {

    private static final Logger log = Logger.getLogger(PomScmLocator.class);

    private static final String HTTPS_GITHUB_COM = "https://github.com/";

    private final String cacheUrl;

    public PomScmLocator(String cacheUrl) {
        this.cacheUrl = cacheUrl;
    }

    @Override
    protected PomClient createPomClient() {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        return new PomClient() {
            @Override
            public Optional<Model> getPom(String group, String artifact, String version) {
                HttpGet get = new HttpGet(
                        cacheUrl + "/" + group.replace(".", "/") + "/" + artifact + "/" + version + "/" + artifact
                                + "-" + version + ".pom");
                try (var response = client.execute(get)) {
                    if (response.getStatusLine().getStatusCode() != 200) {
                        log.errorf("Unexpected status %s for %s:%s:%s", response.getStatusLine().getStatusCode(), group,
                                artifact,
                                version);
                        return Optional.empty();
                    }
                    try (Reader pomReader = new InputStreamReader(response.getEntity().getContent())) {
                        MavenXpp3Reader reader = new MavenXpp3Reader();
                        Model model = reader.read(pomReader);
                        return Optional.of(model);
                    }

                } catch (Exception e) {
                    log.errorf(e, "Failed to get pom for %s:%s:%s", group, artifact, version);
                    return Optional.empty();
                }
            }

            @Override
            public void close() {
                try {
                    client.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

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
        return new StringSubstitutor(map).replace(str);
    }
}
