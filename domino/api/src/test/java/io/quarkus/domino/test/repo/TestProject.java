package io.quarkus.domino.test.repo;

import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.Objects;

public class TestProject {

    public static TestProject of(String groupId, String version) {
        return new TestProject(groupId, version);
    }

    private final String groupId;
    private final String version;
    private TestModule mainModule;
    private String repoUrl;
    private String tag;

    private TestProject(String groupId, String version) {
        this.groupId = Objects.requireNonNull(groupId);
        this.version = Objects.requireNonNull(version);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getVersion() {
        return version;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public TestModule getMainModule() {
        return mainModule;
    }

    public TestModule createMainModule(String artifactId) {
        if (mainModule != null) {
            throw new RuntimeException("main module has already been initialized");
        }
        return mainModule = new TestModule(this, artifactId);
    }

    public TestModule createParentPom(String artifactId) {
        if (mainModule != null) {
            throw new RuntimeException("main module has already been initialized");
        }
        return mainModule = new TestModule(this, artifactId).setPackaging(ArtifactCoords.TYPE_POM);
    }
}
