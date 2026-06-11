package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.domino.scm.ScmRevisionResolver;
import java.util.regex.Pattern;
import org.eclipse.aether.artifact.Artifact;

/**
 * Groups artifacts released from the google-cloud-java monorepo by their module family.
 * <p>
 * The google-cloud-java repo (github.com/googleapis/google-cloud-java) contains ~250 modules
 * each with their own version. Artifacts across modules must NOT be aligned to each other
 * since they have independent version lineages. This detector assigns each artifact to a
 * family-specific origin so that only artifacts from the same module family are aligned.
 * <p>
 * Without this detector, groupId-level caching in {@link ScmRevisionResolver} would cause
 * all ~700 {@code com.google.api.grpc} artifacts to be grouped under a single origin,
 * leading to a combinatorial explosion of version-probing downloads during alignment.
 */
public class GoogleCloudJavaReleaseIdDetector implements ReleaseIdDetector {

    // Matches API version suffixes like -v3, -v1beta1, -v3beta1, -v2alpha, -v1alpha1
    private static final Pattern API_VERSION_SUFFIX = Pattern.compile("-v\\d+[a-z]*\\d*$");

    @Override
    public ScmRevision detectReleaseId(ScmRevisionResolver releaseResolver, Artifact artifact)
            throws BomDecomposerException {
        String family = extractFamily(artifact);
        if (family == null) {
            return null;
        }
        return ScmRevision.version(
                ScmRepository.ofId("google-cloud-java:" + family),
                artifact.getVersion());
    }

    static String extractFamily(Artifact artifact) {
        final String groupId = artifact.getGroupId();
        final String artifactId = artifact.getArtifactId();

        if (groupId.equals("com.google.api.grpc")) {
            return extractFamilyFromGrpcArtifact(artifactId);
        }
        if (groupId.equals("com.google.cloud")
                && artifactId.startsWith("google-cloud-")
                && !artifactId.startsWith("google-cloud-core")
                && !artifactId.contains("google-iam")) {
            return stripPrefix(artifactId, "google-cloud-");
        }
        return null;
    }

    private static String extractFamilyFromGrpcArtifact(String artifactId) {
        String remainder;
        if (artifactId.startsWith("proto-google-cloud-")) {
            remainder = stripPrefix(artifactId, "proto-google-cloud-");
        } else if (artifactId.startsWith("grpc-google-cloud-")) {
            remainder = stripPrefix(artifactId, "grpc-google-cloud-");
        } else if (artifactId.startsWith("proto-google-")) {
            remainder = stripPrefix(artifactId, "proto-google-");
        } else if (artifactId.startsWith("grpc-google-")) {
            remainder = stripPrefix(artifactId, "grpc-google-");
        } else {
            return null;
        }
        return stripApiVersionSuffix(remainder);
    }

    private static String stripPrefix(String s, String prefix) {
        return s.substring(prefix.length());
    }

    private static String stripApiVersionSuffix(String name) {
        return API_VERSION_SUFFIX.matcher(name).replaceFirst("");
    }
}
