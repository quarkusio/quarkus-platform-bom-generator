package com.redhat.hacbs.recipes.mavenrepo;

import java.util.List;

public class MavenRepositoryInfo {

    private String uri;

    private List<String> repositories;

    public String getUri() {
        return uri;
    }

    public MavenRepositoryInfo setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    public MavenRepositoryInfo setRepositories(List<String> repositories) {
        this.repositories = repositories;
        return this;
    }
}
