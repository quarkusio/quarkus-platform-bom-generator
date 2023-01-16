package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseVersion;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;

public class KnownVPrefixReleaseIdDetector implements ReleaseIdDetector {

    private static final Set<String> GROUP_IDS = Set.of(
            "com.github.luben",
            "com.google.api.grpc",
            "com.google.cloud",
            "com.google.errorprone",
            "com.google.protobuf",
            "com.typesafe.scala-logging",
            "io.fabric8",
            "io.grpc",
            "io.jaegertracing",
            "io.micrometer",
            "joda-time",
            "org.elasticsearch.client",
            "org.mockito",
            "org.quartz-scheduler",
            "org.rocksdb",
            "org.scala-lang",
            "org.scala-lang.modules");

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!GROUP_IDS.contains(artifact.getGroupId())) {
            return null;
        }

        final ReleaseId releaseId = idResolver.defaultReleaseId(artifact);
        final String version = releaseId.version().asString();
        if (version.charAt(0) == 'v') {
            return releaseId;
        }
        return ReleaseIdFactory.create(releaseId.origin(), ReleaseVersion.Factory.version("v" + version));
    }
}
