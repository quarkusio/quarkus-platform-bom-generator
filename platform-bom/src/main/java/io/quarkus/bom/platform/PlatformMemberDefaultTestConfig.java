package io.quarkus.bom.platform;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PlatformMemberDefaultTestConfig {

    protected Boolean skip;
    protected Boolean skipNative;
    protected Boolean skipJvm;
    protected Boolean failsafeMavenPlugin;
    protected String transformWith;
    protected Map<String, String> systemProperties = Map.of();
    protected Map<String, String> jvmSystemProperties = Map.of();
    protected Map<String, String> nativeSystemProperties = Map.of();
    protected Properties pomProperties;
    protected String groups;
    protected String nativeGroups;
    protected List<String> dependencies = List.of();
    protected List<String> testDependencies = List.of();
    protected List<String> jvmIncludes = List.of();
    protected List<String> jvmExcludes = List.of();
    protected List<String> nativeIncludes = List.of();
    protected List<String> nativeExcludes = List.of();
    protected Boolean packageApplication;

    protected String argLine;
    protected String jvmArgLine;
    protected String nativeArgLine;

    protected String testPattern;
    protected String jvmTestPattern;
    protected String nativeTestPattern;

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
        if (overrides.argLine != null) {
            argLine = overrides.argLine;
        }
        if (overrides.jvmArgLine != null) {
            jvmArgLine = overrides.jvmArgLine;
        }
        if (overrides.nativeArgLine != null) {
            nativeArgLine = overrides.nativeArgLine;
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
        if (overrides.testPattern != null) {
            testPattern = overrides.testPattern;
        }
        if (overrides.jvmTestPattern != null) {
            jvmTestPattern = overrides.jvmTestPattern;
        }
        if (overrides.nativeTestPattern != null) {
            nativeTestPattern = overrides.nativeTestPattern;
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

    public void setArgLine(String argLine) {
        this.argLine = argLine;
    }

    public String getArgLine() {
        return argLine;
    }

    public void setJvmArgLine(String argLine) {
        this.jvmArgLine = argLine;
    }

    public String getJvmArgLine() {
        return jvmArgLine;
    }

    public void setNativeArgLine(String argLine) {
        this.nativeArgLine = argLine;
    }

    public String getNativeArgLine() {
        return nativeArgLine;
    }

    public String getTestPattern() {
        return testPattern;
    }

    public void setJvmTestPatter(String testPattern) {
        this.jvmTestPattern = testPattern;
    }

    public String getJvmTestPattern() {
        return jvmTestPattern;
    }

    public void setNativeTestPattern(String testPattern) {
        this.nativeTestPattern = testPattern;
    }

    public String getNativeTestPattern() {
        return nativeTestPattern;
    }
}