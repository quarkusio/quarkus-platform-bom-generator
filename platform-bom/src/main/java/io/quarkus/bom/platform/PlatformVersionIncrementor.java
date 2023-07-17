package io.quarkus.bom.platform;

public interface PlatformVersionIncrementor {

    String nextVersion(String baseVersion, String lastVersion);
}
