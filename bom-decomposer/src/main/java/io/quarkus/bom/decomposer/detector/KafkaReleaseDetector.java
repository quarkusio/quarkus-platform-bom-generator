package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import org.eclipse.aether.artifact.Artifact;

public class KafkaReleaseDetector implements ReleaseIdDetector {

    @Override
    public ReleaseId detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
        if (!artifact.getGroupId().startsWith("org.apache.kafka")) {
            return null;
        }
        // Kafka is published from a Gradle project, so the POM is generated and
        // includes
        // neither parent info nor scm.
        // Some JAR artifacts do include kafka/kafka-xxx-version.properties that
        // includes
        // commitId and version. But for simplicity we are simply using the version of
        // the artifact here
        return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("https://github.com/apache/kafka"),
                ReleaseVersion.Factory.version(ModelUtils.getVersion(idResolver.model(artifact))));
    }
}
