package com.redhat.hacbs.recipes.scm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScmInfoTest {
    @Test
    public void testCreateScmInfo() {
        ScmInfo scm = new ScmInfo("git", "https://github.com/quarkusio/quarkus.git");
        Assertions.assertEquals("https://github.com/quarkusio/quarkus.git", scm.getUri());
        Assertions.assertNull(scm.getBuildNameFragment());
    }

    @Test
    public void testCreateScmInfoFragment() {
        ScmInfo scm = new ScmInfo("git", "https://github.com/quarkusio/quarkus.git#foo");
        Assertions.assertEquals("https://github.com/quarkusio/quarkus.git", scm.getUriWithoutFragment());
        Assertions.assertEquals("https://github.com/quarkusio/quarkus.git#foo", scm.getUri());
        Assertions.assertEquals("foo", scm.getBuildNameFragment());
    }
}
