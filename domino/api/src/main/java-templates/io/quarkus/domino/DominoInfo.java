package io.quarkus.domino;

public interface DominoInfo {
    String VERSION = "${project.version}";
    String PROJECT_NAME = "${project.name}";
    String ORGANIZATION_NAME = "${project.organization.name}";
}
