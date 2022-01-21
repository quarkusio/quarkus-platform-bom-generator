package io.quarkus.bom.platform;

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
    protected List<String> jvmIncludes = Collections.emptyList();
    protected List<String> jvmExcludes = Collections.emptyList();
    protected List<String> nativeIncludes = Collections.emptyList();
    protected List<String> nativeExcludes = Collections.emptyList();
    protected Boolean packageApplication;

    public PlatformMemberDefaultTestConfig() {
        super();
    }

    void applyOverrides(PlatformMemberDefaultTestConfig overrides) {
        if (overrides.skip != null) {
            skip = overrides.skip;
        }
        if (overrides.skipNative != null) {
            skipNative = overrides.skipNative;
        }
        if (overrides.skipJvm != null) {
            skipJvm = overrides.skipJvm;
        }
        if (overrides.failsafeMavenPlugin != null) {
            failsafeMavenPlugin = overrides.failsafeMavenPlugin;
        }
        if (overrides.transformWith != null) {
            transformWith = overrides.transformWith;
        }
        if (!overrides.systemProperties.isEmpty()) {
            if (systemProperties.isEmpty()) {
                systemProperties = overrides.systemProperties;
            } else {
                systemProperties.putAll(overrides.systemProperties);
            }
        }
        if (!overrides.jvmSystemProperties.isEmpty()) {
            if (jvmSystemProperties.isEmpty()) {
                jvmSystemProperties = overrides.jvmSystemProperties;
            } else {
                jvmSystemProperties.putAll(overrides.jvmSystemProperties);
            }
        }
        if (!overrides.nativeSystemProperties.isEmpty()) {
            if (nativeSystemProperties.isEmpty()) {
                nativeSystemProperties = overrides.nativeSystemProperties;
            } else {
                nativeSystemProperties.putAll(overrides.nativeSystemProperties);
            }
        }
        if (overrides.pomProperties != null && !overrides.pomProperties.isEmpty()) {
            if (pomProperties == null || pomProperties.isEmpty()) {
                pomProperties = overrides.pomProperties;
            } else {
                pomProperties.putAll(overrides.pomProperties);
            }
        }
        if (overrides.groups != null) {
            groups = overrides.groups;
        }
        if (overrides.nativeGroups != null) {
            nativeGroups = overrides.nativeGroups;
        }
        if (!overrides.dependencies.isEmpty()) {
            if (dependencies.isEmpty()) {
                dependencies = overrides.dependencies;
            } else {
                dependencies.addAll(overrides.dependencies);
            }
        }
        if (!overrides.testDependencies.isEmpty()) {
            if (testDependencies.isEmpty()) {
                testDependencies = overrides.testDependencies;
            } else {
                testDependencies.addAll(overrides.testDependencies);
            }
        }
        if (!overrides.jvmIncludes.isEmpty()) {
            if (jvmIncludes.isEmpty()) {
                jvmIncludes = overrides.jvmIncludes;
            } else {
                jvmIncludes.addAll(overrides.jvmIncludes);
            }
        }
        if (!overrides.jvmExcludes.isEmpty()) {
            if (jvmExcludes.isEmpty()) {
                jvmExcludes = overrides.jvmExcludes;
            } else {
                jvmExcludes.addAll(overrides.jvmExcludes);
            }
        }
        if (!overrides.nativeIncludes.isEmpty()) {
            if (nativeIncludes.isEmpty()) {
                nativeIncludes = overrides.jvmIncludes;
            } else {
                nativeIncludes.addAll(overrides.jvmIncludes);
            }
        }
        if (!overrides.nativeExcludes.isEmpty()) {
            if (nativeExcludes.isEmpty()) {
                nativeExcludes = overrides.jvmExcludes;
            } else {
                nativeExcludes.addAll(overrides.jvmExcludes);
            }
        }
        if (overrides.packageApplication != null) {
            packageApplication = overrides.packageApplication;
        }
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

    public List<String> getJvmIncludes() {
        return jvmIncludes;
    }

    public void setJvmIncludes(List<String> jvmIncludes) {
        this.jvmIncludes = jvmIncludes;
    }

    public List<String> getJvmExcludes() {
        return jvmExcludes;
    }

    public void setJvmExcludes(List<String> jvmExcludes) {
        this.jvmExcludes = jvmExcludes;
    }

    public List<String> getNativeIncludes() {
        return nativeIncludes;
    }

    public void setNativeIncludes(List<String> nativeIncludes) {
        this.nativeIncludes = nativeIncludes;
    }

    public List<String> getNativeExcludes() {
        return nativeExcludes;
    }

    public void setNativeExcludes(List<String> nativeExcludes) {
        this.nativeExcludes = nativeExcludes;
    }

    public void setPackageApplication(boolean packageApplication) {
        this.packageApplication = packageApplication;
    }

    public boolean isPackageApplication() {
        return packageApplication == null ? false : packageApplication;
    }
}