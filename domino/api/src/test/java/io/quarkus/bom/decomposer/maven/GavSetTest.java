package io.quarkus.bom.decomposer.maven;

import io.quarkus.domino.ArtifactSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GavSetTest {

    @Test
    public void defaults() {
        ArtifactSet set = ArtifactSet.builder().build();
        Assertions.assertTrue(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
    }

    @Test
    public void excludeArtifact() {
        ArtifactSet set = ArtifactSet.builder() //
                .exclude("org.group1:artifact1") //
                .build();
        Assertions.assertFalse(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertTrue(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));

        Assertions.assertTrue(set.contains("org.group2", "artifact2", "jar", null, "5.6.7"));
        Assertions.assertTrue(set.contains("org.group2", "artifact3", "jar", null, "6.7.8"));

        Assertions.assertTrue(set.contains("com.group3", "artifact4", "jar", null, "5.6.7"));
    }

    @Test
    public void excludeGroups() {
        ArtifactSet set = ArtifactSet.builder() //
                .exclude("org.group1") //
                .exclude("org.group2") //
                .build();
        Assertions.assertFalse(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertFalse(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));

        Assertions.assertFalse(set.contains("org.group2", "artifact2", "jar", null, "5.6.7"));
        Assertions.assertFalse(set.contains("org.group2", "artifact3", "jar", null, "6.7.8"));

        Assertions.assertTrue(set.contains("com.group3", "artifact4", "jar", null, "5.6.7"));

    }

    @Test
    public void includeArtifact() {
        ArtifactSet set = ArtifactSet.builder() //
                .include("org.group1:artifact1") //
                .build();
        Assertions.assertTrue(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertFalse(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));

        Assertions.assertFalse(set.contains("org.group2", "artifact2", "jar", null, "5.6.7"));
        Assertions.assertFalse(set.contains("org.group2", "artifact3", "jar", null, "6.7.8"));

        Assertions.assertFalse(set.contains("com.group3", "artifact4", "jar", null, "5.6.7"));
    }

    @Test
    public void includeExcludeGroups() {
        ArtifactSet set = ArtifactSet.builder() //
                .include("org.group1") //
                .exclude("org.group2") //
                .build();
        Assertions.assertTrue(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertTrue(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));

        Assertions.assertFalse(set.contains("org.group2", "artifact2", "jar", null, "5.6.7"));
        Assertions.assertFalse(set.contains("org.group2", "artifact3", "jar", null, "6.7.8"));

        Assertions.assertFalse(set.contains("com.group3", "artifact4", "jar", null, "5.6.7"));

    }

    @Test
    public void includeGroup() {
        ArtifactSet set = ArtifactSet.builder() //
                .include("org.group1") //
                .build();
        Assertions.assertTrue(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertTrue(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));

        Assertions.assertFalse(set.contains("org.group2", "artifact2", "jar", null, "5.6.7"));
    }

    @Test
    public void includeGroups() {
        ArtifactSet set = ArtifactSet.builder() //
                .include("org.group1") //
                .include("org.group2") //
                .build();
        Assertions.assertTrue(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertTrue(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));

        Assertions.assertTrue(set.contains("org.group2", "artifact2", "jar", null, "5.6.7"));
        Assertions.assertTrue(set.contains("org.group2", "artifact3", "jar", null, "6.7.8"));

        Assertions.assertFalse(set.contains("com.group3", "artifact4", "jar", null, "5.6.7"));

    }

    @Test
    public void includeGroupsExcludeArtifact() {
        ArtifactSet set = ArtifactSet.builder() //
                .include("org.group1") //
                .include("org.group2") //
                .include("com.group3") //
                .exclude("org.group1:artifact2") //
                .exclude("org.group1:artifact3") //
                .exclude("org.group2:artifact2") //
                .exclude("org.group2:artifact3") //
                .build();
        Assertions.assertTrue(set.contains("org.group1", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertFalse(set.contains("org.group1", "artifact2", "jar", null, "2.3.4"));
        Assertions.assertFalse(set.contains("org.group1", "artifact3", "jar", null, "2.3.4"));

        Assertions.assertTrue(set.contains("org.group2", "artifact1", "jar", null, "1.2.3"));
        Assertions.assertFalse(set.contains("org.group2", "artifact2", "jar", null, "2.3.4"));
        Assertions.assertFalse(set.contains("org.group2", "artifact3", "jar", null, "2.3.4"));

        Assertions.assertTrue(set.contains("com.group3", "artifact1", "jar", null, "5.6.7"));
        Assertions.assertTrue(set.contains("com.group3", "artifact2", "jar", null, "5.6.7"));
        Assertions.assertTrue(set.contains("com.group3", "artifact3", "jar", null, "5.6.7"));
        Assertions.assertTrue(set.contains("com.group3", "artifact4", "jar", null, "5.6.7"));
    }
}
