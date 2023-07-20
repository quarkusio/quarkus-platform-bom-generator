package io.quarkus.bom.platform.version;

public interface PlatformVersionIncrementor {

    String nextVersion(String baseVersion, String lastVersion);
}
