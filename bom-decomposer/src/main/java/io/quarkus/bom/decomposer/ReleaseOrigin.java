package io.quarkus.bom.decomposer;

import io.quarkus.domino.scm.ScmRepository;

/**
 * @deprecated in favor of {@link ScmRepository}
 */
@Deprecated(forRemoval = true)
public interface ReleaseOrigin {

    class Factory {
        public static ScmRepository scmConnection(String connection) {
            return ScmRepository.ofUrl(connection);
        }

        public static ScmRepository ga(String groupId, String artifactId) {
            return ScmRepository.ofId(groupId + ":" + artifactId);
        }
    }

    boolean isUrl();
}
