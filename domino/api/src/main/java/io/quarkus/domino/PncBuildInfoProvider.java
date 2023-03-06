package io.quarkus.domino;

import io.quarkus.bootstrap.util.PropertyUtils;
import io.quarkus.domino.manifest.PncArtifactBuildInfo;
import io.quarkus.maven.dependency.GAV;
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

    public PncArtifactBuildInfo getBuildInfo(GAV gav) {
        if (!RhVersionPattern.isRhVersion(gav.getVersion())) {
            return null;
        }
        final Path cachedJson = cacheDir.resolve(gav.getGroupId()).resolve(gav.getArtifactId()).resolve(gav.getVersion())
                .resolve(PNC_BUILD_INFO_JSON);
        if (Files.exists(cachedJson)) {
            return PncArtifactBuildInfo.deserialize(cachedJson);
        }

        final URL url;
        try {
            url = new URL("https", "orch.psi.redhat.com", 443,
                    "/pnc-rest/v2/artifacts?q=identifier==%22"
                            + gav.getGroupId() + ":"
                            + gav.getArtifactId() + ":pom:"
                            + gav.getVersion()
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
            throw new UncheckedIOException("Failed to connect to " + url, e);
        }

        try {
            PncArtifactBuildInfo.serialize(buildInfo, cachedJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize " + buildInfo + " to " + cachedJson, e);
        }
        return buildInfo;
    }
}
