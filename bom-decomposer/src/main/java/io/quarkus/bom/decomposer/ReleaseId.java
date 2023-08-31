package io.quarkus.bom.decomposer;

import io.quarkus.domino.scm.ScmRevision;

/**
 * @deprecated in favor of {@link ScmRevision}
 */
@Deprecated(forRemoval = true)
public interface ReleaseId {

    ReleaseOrigin origin();

    ReleaseVersion version();
}
