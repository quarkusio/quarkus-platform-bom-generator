package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PlatformMemberDefaultTestConfig {

    protected Boolean enabled;
    protected Boolean nativeEnabled;
    protected Boolean jvmEnabled;
    protected Boolean failsafeMavenPlugin;
    protected String transformWith;
    protected Map<String, String> systemProperties = Collections.emptyMap();
    protected Map<String, String> jvmSystemProperties = Collections.emptyMap();
    protected Map<String, String> nativeSystemProperties = Collections.emptyMap();
    protected Properties pomProperties;
    protected String groups;
    protected String nativeGroups;
    protected List<String> dependencies = Collections.emptyList();
    protected List<String> testDependencies = Collections.emptyList();

    public PlatformMemberDefaultTestConfig() {
        super();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled == null ? true : enabled;
    }

    public void setNativeEnabled(boolean enabled) {
        this.nativeEnabled = enabled;
    }

    public boolean isNativeEnabled() {
        return nativeEnabled == null ? true : nativeEnabled;
    }

    public void setJvmEnabled(boolean enabled) {
        this.jvmEnabled = enabled;
    }

    public boolean isJvmEnabled() {
        return jvmEnabled == null ? true : jvmEnabled;
    }

    public void setMavenFailsafePlugin(boolean failsafeMavenPlugin) {
        this.failsafeMavenPlugin = failsafeMavenPlugin;
    }

    public boolean isMavenFailsafePlugin() {
        return failsafeMavenPlugin == null ? false : failsafeMavenPlugin;
    }

    public void setTransformWith(String transformWith) {
        this.transformWith = transformWith;
    }

    public String getTransformWith() {
        return transformWith;
    }

    public void setSystemProperties(Map<String, String> systemProperties) {
        this.systemProperties = systemProperties;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public void setJvmSystemProperties(Map<String, String> systemProperties) {
        this.jvmSystemProperties = systemProperties;
    }

    public Map<String, String> getJvmSystemProperties() {
        return jvmSystemProperties;
    }

    public void setNativeSystemProperties(Map<String, String> systemProperties) {
        this.nativeSystemProperties = systemProperties;
    }

    public Map<String, String> getNativeSystemProperties() {
        return nativeSystemProperties;
    }

    public void setPomProperties(Properties pomProperties) {
        this.pomProperties = pomProperties;
    }

    public Properties getPomProperties() {
        return pomProperties == null ? pomProperties = new Properties() : pomProperties;
    }

    public void setGroups(String groups) {
        this.groups = groups;
    }

    public String getGroups() {
        return groups;
    }

    public void setNativeGroups(String groups) {
        this.nativeGroups = groups;
    }

    public String getNativeGroups() {
        return nativeGroups;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setTestDependencies(List<String> testDependencies) {
        this.testDependencies = testDependencies;
    }

    public List<String> getTestDependencies() {
        return testDependencies;
    }
}