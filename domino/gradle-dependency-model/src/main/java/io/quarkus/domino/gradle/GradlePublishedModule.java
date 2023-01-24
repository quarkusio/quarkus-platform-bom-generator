package io.quarkus.domino.gradle;

public interface GradlePublishedModule {

    static GradlePublishedModule of(String group, String name, String version, String projectPath) {
        return new PublishedModule(group, name, version, projectPath);
    }

    /**
     * Group under which a module is published
     * 
     * @return group under which a module is published
     */
    String getGroup();

    /**
     * Name under which a module is published. Could be different from the original module name.
     * 
     * @return name under which a module is published
     */
    String getName();

    String getVersion();

    String getProjectPath();
}
