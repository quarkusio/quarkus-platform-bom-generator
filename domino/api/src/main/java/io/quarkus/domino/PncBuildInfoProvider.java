package io.quarkus.domino;

import io.quarkus.bootstrap.util.PropertyUtils;
import io.quarkus.domino.manifest.PncArtifactBuildInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.logging.Logger;

public class PncBuildInfoProvider {

    private static final String DOT_DOMINO = ".domino";
    private static final String PNC_BUILD_INFO = "pnc-build-info";
    private static final String PNC_BUILD_INFO_JSON = PNC_BUILD_INFO + ".json";

    private static Logger log = Logger.getLogger(PncBuildInfoProvider.class);

    private final Path cacheDir;

    public PncBuildInfoProvider() {
        cacheDir = Path.of(PropertyUtils.getUserHome()).resolve(DOT_DOMINO).resolve(PNC_BUILD_INFO);
    }

    public PncArtifactBuildInfo getBuildInfo(String groupId, String artifactId, String version) {
        if (!RhVersionPattern.isRhVersion(version)) {
            return null;
        }
        final Path cachedJson = cacheDir.resolve(groupId).resolve(artifactId).resolve(version)
                .resolve(PNC_BUILD_INFO_JSON);
        if (Files.exists(cachedJson)) {
            return PncArtifactBuildInfo.deserialize(cachedJson);
        }

        final URL url;
        try {
            url = new URL("https", "orch.psi.redhat.com", 443,
                    "/pnc-rest/v2/artifacts?q=identifier==%22"
                            + groupId + ":"
                            + artifactId + ":pom:"
                            + version
                            + "%22");
            log.infof("Requesting build info %s", url);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse URL", e);
        }
        final PncArtifactBuildInfo buildInfo;
        try {
            final URLConnection connection = url.openConnection();
            buildInfo = PncArtifactBuildInfo.deserialize(connection.getInputStream());
        } catch (IOException e) {
            log.warn("Failed to connect to " + url + ": " + e.getLocalizedMessage());
            return null;
        }

        try {
            PncArtifactBuildInfo.serialize(buildInfo, cachedJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize " + buildInfo + " to " + cachedJson, e);
        }
        return buildInfo;
    }
}
