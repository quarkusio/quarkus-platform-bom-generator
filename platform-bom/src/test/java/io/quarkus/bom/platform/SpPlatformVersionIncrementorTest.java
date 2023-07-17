package io.quarkus.bom.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class SpPlatformVersionIncrementorTest {

    @Test
    public void implicitFinalQualifierNoLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0", null)).isEqualTo("1.0.0.SP1");
    }

    @Test
    public void finalQualifierNoLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0.Final", null)).isEqualTo("1.0.0.SP1");
    }

    @Test
    public void finalQualifierMatchingLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0.Final", "1.0.0.Final")).isEqualTo("1.0.0.SP1");
    }

    @Test
    public void baseVersionWithSpLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0", "1.0.0.SP2")).isEqualTo("1.0.0.SP3");
    }

    @Test
    public void newerBaseVersionWithOlderSpLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("2.0.0", "1.0.0.SP2")).isEqualTo("2.0.0.SP1");
    }

    @Test
    public void rhFinalQualifierNoLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0.Final-redhat-12345", null)).isEqualTo("1.0.0.SP1-redhat-12345");
    }

    @Test
    public void rhImplicitFinalQualifierMatchingLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0.redhat-12345", "1.0.0.redhat-12345")).isEqualTo("1.0.0.SP1.redhat-12345");
    }

    @Test
    public void rhBaseVersionWithSpLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("1.0.0.redhat-12345", "1.0.0.SP2.redhat-12345")).isEqualTo("1.0.0.SP3.redhat-12345");
    }

    @Test
    public void rhNewerBaseVersionWithOlderSpLastBuild() {
        var incrementor = new SpPlatformVersionIncrementor();
        assertThat(incrementor.nextVersion("2.0.0.redhat-12345", "1.0.0.SP2.redhat-12345")).isEqualTo("2.0.0.SP1.redhat-12345");
    }

}
