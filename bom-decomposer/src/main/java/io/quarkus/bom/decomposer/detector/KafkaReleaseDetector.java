package io.quarkus.bom.decomposer.detector;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import org.eclipse.aether.artifact.Artifact;

public class KafkaReleaseDetector implements ReleaseIdDetector {

    @Override
    public ScmRevision detectReleaseId(ReleaseIdResolver idResolver, Artifact artifact) throws BomDecomposerException {
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
        return ScmRevision.tag(ScmRepository.ofUrl("https://github.com/apache/kafka"),
                ModelUtils.getVersion(idResolver.model(artifact)));
    }
}
