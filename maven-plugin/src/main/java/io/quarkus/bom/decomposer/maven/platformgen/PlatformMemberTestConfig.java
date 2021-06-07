package io.quarkus.bom.decomposer.maven.platformgen;

import java.util.Map;

public class PlatformMemberTestConfig extends PlatformMemberDefaultTestConfig {

    private String artifact;
    private Boolean excluded;

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    public String getArtifact() {
        return artifact;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public boolean isExcluded() {
        return excluded == null ? false : excluded.booleanValue();
    }

    void applyOverrides(PlatformMemberTestConfig overrides) {
        super.applyOverrides(overrides);
        if (overrides.excluded != null) {
            excluded = overrides.excluded;
        }
    }

    void applyDefaults(PlatformMemberDefaultTestConfig defaults) {
        if (skip == null) {
            skip = defaults.skip;
        }
        if (skipNative == null) {
            skipNative = defaults.skipNative;
        }
        if (skipJvm == null) {
            skipJvm = defaults.skipJvm;
        }
        if (failsafeMavenPlugin == null) {
            failsafeMavenPlugin = defaults.failsafeMavenPlugin;
        }
        if (transformWith == null) {
            transformWith = defaults.transformWith;
        }
        if (!defaults.systemProperties.isEmpty()) {
            if (systemProperties.isEmpty()) {
                systemProperties = defaults.systemProperties;
            } else {
                for (Map.Entry<String, String> prop : defaults.systemProperties.entrySet()) {
                    if (!systemProperties.containsKey(prop.getKey())) {
                        systemProperties.put(prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        if (!defaults.jvmSystemProperties.isEmpty()) {
            if (jvmSystemProperties.isEmpty()) {
                jvmSystemProperties = defaults.jvmSystemProperties;
            } else {
                for (Map.Entry<String, String> prop : defaults.jvmSystemProperties.entrySet()) {
                    if (!jvmSystemProperties.containsKey(prop.getKey())) {
                        jvmSystemProperties.put(prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        if (!defaults.nativeSystemProperties.isEmpty()) {
            if (nativeSystemProperties.isEmpty()) {
                nativeSystemProperties = defaults.nativeSystemProperties;
            } else {
                for (Map.Entry<String, String> prop : defaults.nativeSystemProperties.entrySet()) {
                    if (!nativeSystemProperties.containsKey(prop.getKey())) {
                        nativeSystemProperties.put(prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        if (defaults.pomProperties != null && !defaults.pomProperties.isEmpty()) {
            if (pomProperties == null || pomProperties.isEmpty()) {
                pomProperties = defaults.pomProperties;
            } else {
                for (Map.Entry<?, ?> prop : defaults.pomProperties.entrySet()) {
                    if (!pomProperties.containsKey(prop.getKey().toString())) {
                        pomProperties.put(prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        if (groups == null) {
            groups = defaults.groups;
        }
        if (nativeGroups == null) {
            nativeGroups = defaults.nativeGroups;
        }
        if (!defaults.dependencies.isEmpty()) {
            if (dependencies.isEmpty()) {
                dependencies = defaults.dependencies;
            } else {
                dependencies.addAll(defaults.dependencies);
            }
        }
        if (!defaults.testDependencies.isEmpty()) {
            if (testDependencies.isEmpty()) {
                testDependencies = defaults.testDependencies;
            } else {
                testDependencies.addAll(defaults.testDependencies);
            }
        }
    }
}
