package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlatformMemberConfig {

    private String name;
    private String bom;
    private List<String> dependencyManagement = Collections.emptyList();
    private boolean enabled = true;

    private PlatformMemberReleaseConfig release;

    private PlatformMemberDefaultTestConfig defaultTestConfig;
    private List<PlatformMemberTestConfig> tests = new ArrayList<>();
    private String testCatalogArtifact;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setBom(String bom) {
        this.bom = bom;
    }

    public String getBom() {
        return bom;
    }

    public void setDependencyManagement(List<String> dependencyManagement) {
        this.dependencyManagement = dependencyManagement;
    }

    public List<String> getDependencyManagement() {
        return dependencyManagement;
    }

    public void setRelease(PlatformMemberReleaseConfig release) {
        this.release = release;
    }

    public PlatformMemberReleaseConfig getRelease() {
        return release;
    }

    public void setTestCatalogArtifact(String testCatalogArtifact) {
        this.testCatalogArtifact = testCatalogArtifact;
    }

    public String getTestCatalogArtifact() {
        return testCatalogArtifact;
    }

    public void setDefaultTestConfig(PlatformMemberDefaultTestConfig defaultTestConfig) {
        this.defaultTestConfig = defaultTestConfig;
    }

    public PlatformMemberDefaultTestConfig getDefaultTestConfig() {
        return defaultTestConfig;
    }

    public void addTest(PlatformMemberTestConfig test) {
        tests.add(test);
    }

    public List<PlatformMemberTestConfig> getTests() {
        return tests;
    }

    public boolean hasTests() {
        return !tests.isEmpty() || testCatalogArtifact != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
