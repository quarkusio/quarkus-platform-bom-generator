package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlatformMemberConfig {

    private String name;
    private String bom;
    private List<String> dependencyManagement = Collections.emptyList();
    private Boolean enabled;

    private PlatformMemberReleaseConfig release;

    private PlatformMemberDefaultTestConfig defaultTestConfig;
    private Collection<PlatformMemberTestConfig> tests = new ArrayList<>();
    private String testCatalogArtifact;

    private String extensionList;

    void applyOverrides(PlatformMemberConfig overrides) {
        if (overrides.bom != null) {
            bom = overrides.bom;
        }
        if (!overrides.dependencyManagement.isEmpty()) {
            if (dependencyManagement.isEmpty()) {
                dependencyManagement = overrides.dependencyManagement;
            } else {
                overrides.dependencyManagement.stream().filter(s -> dependencyManagement.contains(s))
                        .forEach(dependencyManagement::add);
            }
        }
        if (overrides.enabled != null) {
            enabled = overrides.enabled;
        }
        if (overrides.release != null) {
            release.applyOverrides(overrides.release);
        }
        if (overrides.defaultTestConfig != null) {
            if (defaultTestConfig == null) {
                defaultTestConfig = overrides.defaultTestConfig;
            } else {
                defaultTestConfig.applyOverrides(overrides.defaultTestConfig);
            }
        }
        if (!overrides.tests.isEmpty()) {
            if (tests.isEmpty()) {
                tests = overrides.tests;
            } else {
                final Map<String, PlatformMemberTestConfig> map = new LinkedHashMap<>(tests.size() + overrides.tests.size());
                for (PlatformMemberTestConfig t : tests) {
                    map.put(t.getArtifact(), t);
                }
                for (PlatformMemberTestConfig t : overrides.tests) {
                    final PlatformMemberTestConfig original = map.put(t.getArtifact(), t);
                    if (original != null) {
                        original.applyOverrides(t);
                    }
                }
                tests = map.values();
            }
        }
        if (overrides.testCatalogArtifact != null) {
            testCatalogArtifact = overrides.testCatalogArtifact;
        }
        if (overrides.extensionList != null) {
            extensionList = overrides.extensionList;
        }
    }

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

    public Collection<PlatformMemberTestConfig> getTests() {
        return tests;
    }

    public boolean hasTests() {
        return !tests.isEmpty() || testCatalogArtifact != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled == null ? true : enabled.booleanValue();
    }

    public void setExtensionList(String extensionList) {
        this.extensionList = extensionList;
    }

    /**
     * Path to a JSON file containing a list of extensions the member BOM should be limited to.
     * 
     * @return path to a JSON file containing a list of extensions the member BOM should be limited to
     */
    public String getExtensionList() {
        return extensionList;
    }
}
