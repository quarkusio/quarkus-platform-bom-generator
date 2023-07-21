package io.quarkus.bom.platform.version;

public interface PlatformVersionIncrementor {

    String nextVersion(String groupId, String artifactId, String baseVersion, String lastVersion);
}
