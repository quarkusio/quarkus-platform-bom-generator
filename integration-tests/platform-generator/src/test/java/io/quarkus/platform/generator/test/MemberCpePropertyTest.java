package io.quarkus.platform.generator.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.platform.generator.PlatformTestProjectGenerator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MemberCpePropertyTest {

    private static final String CAMEL_CPE = "cpe:2.3:a:acme:camel_quarkus:1.0:*:*:*:*:*:*:*";

    @TempDir
    Path workingDir;

    @Test
    void memberCpeIsExposedAsPlatformProperty() throws Exception {
        var platformGenerator = PlatformTestProjectGenerator.newInstance();

        var camelProject = platformGenerator.configureProject("org.camel", "camel-parent", "1.0")
                .setScm("https://camel.org", "1.0");
        var camelAtom = camelProject.addQuarkusExtensionRuntimeModule("camel-atom");
        var camelBom = camelProject.addPomModule("camel-bom")
                .addVersionConstraint(camelAtom);

        var amqProject = platformGenerator.configureProject("org.amq", "amq-parent", "1.0")
                .setScm("https://amq.org", "1.0");
        var amqJms = amqProject.addQuarkusExtensionRuntimeModule("amq-jms");
        var amqBom = amqProject.addPomModule("amq-bom")
                .addVersionConstraint(amqJms);

        // Camel declares a product CPE, AMQ does not
        platformGenerator.configureMember(camelBom).setName("Camel").setCpe(CAMEL_CPE);
        platformGenerator.configureMember(amqBom).setName("AMQ");

        var platform = platformGenerator
                .setProjectDir(workingDir)
                .generateProject()
                .build();

        // The Camel member's own generated properties carry its CPE, keyed by the generated member BOM GA
        var camel = platform.getMember("Camel");
        var expectedKey = "platform." + PlatformTestProjectGenerator.DEFAULT_GROUP_ID + ".quarkus-camel-bom.cpe";
        assertThat(camel.getProperties()).containsEntry(expectedKey, CAMEL_CPE);

        // A member without a configured CPE gets no .cpe property
        var amq = platform.getMember("AMQ");
        assertThat(amq.getProperties().stringPropertyNames())
                .noneMatch(name -> name.endsWith(".cpe"));
    }
}
