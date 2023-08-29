package io.quarkus.domino.pnc;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.domino.RhVersionPattern;
import io.quarkus.maven.dependency.GAV;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class PncVersionProvider {

    private static final String LATEST_VERSION_REQUEST_URL = "https://da.psi.redhat.com/da/rest/v-1/lookup/maven/latest";

    private static volatile ObjectMapper mapper;

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            om.enable(JsonParser.Feature.ALLOW_COMMENTS);
            om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper = om;
        }
        return mapper;
    }

    public static String getNextRedHatBuildVersion(String groupId, String artifactId, String version) {
        var v = getLastRedHatBuildVersion(groupId, artifactId, version);
        if (v == null) {
            version = RhVersionPattern.ensureNoRhQualifier(version);
            var av = new DefaultArtifactVersion(version);
            if (av.getQualifier() == null) {
                return version + ".redhat-00001";
            }
            return version + "-redhat-00001";
        }
        int i = v.indexOf("redhat-");
        if (i < 0) {
            throw new RuntimeException("Failed to locate 'redhat-' in " + v);
        }
        var buildNumberString = v.substring(i + "redhat-".length());
        var buildNumber = Integer.parseInt(buildNumberString) + 1;
        return v.substring(0, i + "redhat-".length()) + String.format("%05d", buildNumber);
    }

    public static String getLastRedHatBuildVersion(String groupId, String artifactId, String version) {

        final String jsonRequest = getLatestVersionJsonRequest(groupId, artifactId, version);
        final byte[] postData = jsonRequest.getBytes(StandardCharsets.UTF_8);

        final HttpURLConnection conn = initConnection(LATEST_VERSION_REQUEST_URL);
        conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.write(postData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final List<PncArtifactLatestVersion> versions;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            versions = Arrays.asList(getMapper().readerForArrayOf(PncArtifactLatestVersion.class).readValue(reader));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (versions.size() != 1) {
            throw new RuntimeException("Expected one version in response but got " + versions);
        }
        return versions.get(0).getLatestVersion();
    }

    public static Collection<PncArtifactLatestVersion> getLastRedHatBuildVersions(Collection<GAV> artifactList) {
        final String jsonRequest = getLatestVersionJsonRequest(artifactList);
        final byte[] postData = jsonRequest.getBytes(StandardCharsets.UTF_8);

        final HttpURLConnection conn = initConnection(LATEST_VERSION_REQUEST_URL);
        conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.write(postData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final List<PncArtifactLatestVersion> versions;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            versions = Arrays.asList(getMapper().readerForArrayOf(PncArtifactLatestVersion.class).readValue(reader));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return versions;
    }

    private static HttpURLConnection initConnection(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LATEST_VERSION_REQUEST_URL).openConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        conn.setDoOutput(true);
        try {
            conn.setRequestMethod("POST");
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setUseCaches(false);
        return conn;
    }

    private static String getLatestVersionJsonRequest(Collection<GAV> artifactList) {
        if (artifactList.isEmpty()) {
            return null;
        }
        var sb = new StringBuilder();
        sb.append("{\"mode\":\"PERSISTENT\",\"artifacts\":[");
        var i = artifactList.iterator();
        var c = i.next();
        sb.append(toJson(c));
        if (i.hasNext()) {
            final Set<GAV> gavs = new HashSet<>(artifactList.size());
            gavs.add(c);
            while (i.hasNext()) {
                c = i.next();
                if (gavs.add(c)) {
                    sb.append(",").append(toJson(c));
                }
            }
        }
        return sb.append("]}").toString();
    }

    private static String toJson(GAV gav) {
        return "{\"groupId\":\"" + gav.getGroupId()
                + "\",\"artifactId\":\"" + gav.getArtifactId()
                + "\",\"version\":\"" + gav.getVersion() + "\"}";
    }

    private static String getLatestVersionJsonRequest(String groupId, String artifactId, String version) {
        return "{\"mode\":\"PERSISTENT\",\"artifacts\":[{\"groupId\":\"" + groupId + "\",\"artifactId\":\"" + artifactId
                + "\",\"version\":\"" + version + "\"}]}";
    }
}
