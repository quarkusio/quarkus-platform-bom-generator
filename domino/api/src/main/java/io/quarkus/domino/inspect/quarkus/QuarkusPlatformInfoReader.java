package io.quarkus.domino.inspect.quarkus;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;

public class QuarkusPlatformInfoReader {

    private static volatile ObjectMapper mapper;

    private static final String PLATFORM_COMMUNITY_GROUP_ID = "io.quarkus.platform";
    private static final String PLATFORM_REDHAT_GROUP_ID = "com.redhat.quarkus.platform";
    private static final String QUARKUS_BOM = "quarkus-bom";
    private static final String QUARKUS_PLATFORM_DESCRIPTOR = "-quarkus-platform-descriptor";
    private static final String REDHAT = "redhat";

    public static Builder builder() {
        return new QuarkusPlatformInfoReader().new Builder();
    }

    public class Builder {

        private boolean built;

        private Builder() {
        }

        public Builder setResolver(MavenArtifactResolver resolver) {
            ensureNotBuilt();
            QuarkusPlatformInfoReader.this.resolver = resolver;
            return this;
        }

        public Builder setVersion(String version) {
            ensureNotBuilt();
            QuarkusPlatformInfoReader.this.version = version;
            return this;
        }

        public Builder setPlatformKey(String platformKey) {
            ensureNotBuilt();
            QuarkusPlatformInfoReader.this.platformKey = platformKey;
            return this;
        }

        public QuarkusPlatformInfoReader build() {
            ensureNotBuilt();
            built = true;
            return QuarkusPlatformInfoReader.this;
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new RuntimeException("This builder instance has already been built");
            }
        }
    }

    private MavenArtifactResolver resolver;
    private String platformKey;
    private String version;

    private QuarkusPlatformInfoReader() {
    }

    public QuarkusPlatformInfo readPlatformInfo() {
        if (isBlank(version)) {
            throw new IllegalArgumentException("Platform version wasn't provided");
        }

        if (isBlank(platformKey)) {
            platformKey = version.contains(REDHAT) ? PLATFORM_REDHAT_GROUP_ID : PLATFORM_COMMUNITY_GROUP_ID;
        }

        if (resolver == null) {
            throw new IllegalArgumentException("Artifact resolver was not initialized");
        }

        var coreMember = readMember(platformKey, QUARKUS_BOM, version);
        final List<QuarkusPlatformInfo.Member> allMembers;
        if (coreMember.getRelease() == null) {
            allMembers = new ArrayList<>(1);
            allMembers.add(coreMember);
        } else {
            allMembers = new ArrayList<QuarkusPlatformInfo.Member>(coreMember.getRelease().getMemberBoms().size());
            allMembers.add(coreMember);
            for (var memberBom : coreMember.getRelease().getMemberBoms()) {
                if (!memberBom.equals(coreMember.getBom())) {
                    allMembers.add(readMember(memberBom.getGroupId(), memberBom.getArtifactId(), memberBom.getVersion()));
                }
            }
        }
        return new QuarkusPlatformInfo(coreMember, allMembers,
                ArtifactCoords.jar(coreMember.getBom().getGroupId(), "quarkus-maven-plugin", coreMember.getBom().getVersion()));
    }

    private QuarkusPlatformInfo.Member readMember(String bomGroupId, String bomArtifactId, String bomVersion) {
        final File quarkusBomJson;
        try {
            quarkusBomJson = resolver.resolve(new DefaultArtifact(bomGroupId, bomArtifactId + QUARKUS_PLATFORM_DESCRIPTOR,
                    bomVersion, "json", bomVersion)).getArtifact().getFile();
        } catch (BootstrapMavenException e) {
            throw new RuntimeException(e);
        }
        return QuarkusPlatformInfoReader.readMember(quarkusBomJson);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            om.enable(JsonParser.Feature.ALLOW_COMMENTS);
            om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            om.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper = om;
        }
        return mapper;
    }

    static QuarkusPlatformInfo.Member readMember(File jsonFile) {
        final JsonNode root;
        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
            root = getMapper().readTree(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize " + jsonFile, e);
        }
        return new QuarkusPlatformInfo.Member(
                ArtifactCoords.fromString(readTextValue(root, "bom")),
                readTextValue(root, "quarkus-core-version"),
                readMember(root),
                readRelease(root));
    }

    static List<ArtifactCoords> readMember(JsonNode root) {
        var node = root.get("extensions");
        if (node == null || !node.isArray()) {
            throw new RuntimeException("Failed to locate extensions array in the extension catalog");
        }
        var arr = (ArrayNode) node;
        final List<ArtifactCoords> extCoords = new ArrayList<>(arr.size());
        for (var e : arr) {
            extCoords.add(ArtifactCoords.fromString(readTextValue(e, "artifact")));
        }
        return extCoords;
    }

    private static QuarkusPlatformInfo.Release readRelease(JsonNode root) {
        var node = root.get("metadata");
        if (node == null) {
            return null;
        }
        node = node.get("platform-release");
        if (node == null) {
            return null;
        }
        var members = node.get("members");
        if (members == null || !members.isArray()) {
            throw new RuntimeException("Failed to locate field metadata/platform-release/members");
        }
        var memberArr = (ArrayNode) members;
        var memberBoms = new ArrayList<ArtifactCoords>(memberArr.size());
        for (var m : memberArr) {
            var coords = ArtifactCoords.fromString(m.asText());
            memberBoms.add(ArtifactCoords.pom(coords.getGroupId(),
                    coords.getArtifactId().replace(QUARKUS_PLATFORM_DESCRIPTOR, ""), coords.getVersion()));
        }
        return new QuarkusPlatformInfo.Release(
                readTextValue(node, "platform-key"),
                readTextValue(node, "stream"),
                readTextValue(node, "version"),
                memberBoms);
    }

    private static String readTextValue(JsonNode node, String fieldName) {
        var value = node.get(fieldName);
        if (value == null) {
            throw new RuntimeException("Field " + fieldName + " isn't present");
        }
        return value.textValue();
    }
}
