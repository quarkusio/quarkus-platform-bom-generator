package com.redhat.hacbs.recipes.build;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildRecipeInfoManagerTest {

    @Test
    void parse(@TempDir Path tempDir)
            throws IOException, URISyntaxException {
        BuildRecipeInfoManager buildRecipeInfoManager = new BuildRecipeInfoManager();
        var result = buildRecipeInfoManager.parse(Path.of(
                Objects.requireNonNull(
                        BuildRecipeInfoManagerTest.class.getClassLoader().getResource("build.yaml")).toURI()));
        assertNotNull(result);

        assertEquals(1, result.additionalArgs.size());
        assertEquals("-DskipDocs", result.additionalArgs.get(0));

        File written = new File(tempDir.toFile(), "result.yaml");
        BuildRecipeInfoManager.MAPPER.writeValue(written, result);
        String generated = Files.readString(written.toPath());

        assertEquals("---\n" +
                "additionalArgs:\n" +
                "  - \"-DskipDocs\"\n" +
                "additionalBuilds:\n" +
                "  pureJava:\n" +
                "    additionalArgs:\n" +
                "      - \"-Dlz4-pure-java=true\"\n" +
                "    preBuildScript: \"./autogen.sh\\n/bin/sh -c \\\"$(rpm --eval %configure); $(rpm --eval %__make) $(rpm --eval %_smp_mflags)\\\"\\n\"\n"
                +
                "additionalDownloads:\n" +
                "  - binaryPath: \"only_for_tar/bin\"\n" +
                "    fileName: \"yq\"\n" +
                "    sha256: \"30459aa144a26125a1b22c62760f9b3872123233a5658934f7bd9fe714d7864d\"\n" +
                "    type: \"executable\"\n" +
                "    uri: \"https://github.com/mikefarah/yq/releases/download/v4.30.4/yq_linux_amd64\"\n" +
                "  - packageName: \"glibc-devel\"\n" +
                "    type: \"rpm\"\n" +
                "additionalMemory: 4096\n" +
                "allowedDifferences:\n" +
                "  - \"\\\\Q-:jbossws-common-4.0.0.Final.jar:class:org/jboss/ws/common/CalendarTest\\\\E\"\n" +
                "alternativeArgs:\n" +
                "  - \"'set Global / baseVersionSuffix:=\\\"\\\"'\"\n" +
                "  - \"enableOptimizer\"\n" +
                "enforceVersion: true\n" +
                "repositories:\n" +
                "  - \"caucho\"\n", generated);
    }
}
