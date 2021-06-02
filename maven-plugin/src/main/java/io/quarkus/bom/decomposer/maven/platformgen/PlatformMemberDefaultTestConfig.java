package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PlatformMemberDefaultTestConfig {

    protected Boolean skip;
    protected Boolean skipNative;
    protected Boolean skipJvm;
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

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public boolean isSkip() {
        return skip == null ? false : skip;
    }

    public void setSkipNative(boolean skip) {
        this.skipNative = skip;
    }

    public boolean isSkipNative() {
        return skipNative == null ? false : skipNative;
    }

    public void setSkipJvm(boolean skip) {
        this.skipJvm = skip;
    }

    public boolean isSkipJvm() {
        return skipJvm == null ? false : skipJvm;
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