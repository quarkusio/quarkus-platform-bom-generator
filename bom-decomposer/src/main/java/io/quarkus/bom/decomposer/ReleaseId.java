package io.quarkus.bom.decomposer;

public interface ReleaseId {

    ReleaseOrigin origin();

    ReleaseVersion version();
}
