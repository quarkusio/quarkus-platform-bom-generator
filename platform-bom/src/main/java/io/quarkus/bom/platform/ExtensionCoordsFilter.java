package io.quarkus.bom.platform;

import org.eclipse.aether.artifact.Artifact;

public interface ExtensionCoordsFilter {

    boolean isExcludeFromBom(Artifact a);
}
