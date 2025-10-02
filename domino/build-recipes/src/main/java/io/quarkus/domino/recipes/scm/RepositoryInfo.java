package io.quarkus.domino.recipes.scm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryInfo {
    private static final Logger log = Logger.getLogger(RepositoryInfo.class.getName());

    String type;
    String uri;
    String path;

    boolean privateRepo;

    @JsonIgnore
    String buildNameFragment;

    private List<TagMapping> tagMapping = new ArrayList<>();

    public RepositoryInfo() {
    }

    public RepositoryInfo(String type, String uri) {
        setType(type);
        setUri(uri);
    }

    public RepositoryInfo(String type, String uri, String path) {
        setType(type);
        setUri(uri);
        setPath(path);
    }

    public String getType() {
        return type;
    }

    public String getUri() {
        return uri;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setUri(String uri) {
        this.uri = uri;
        int index = uri.lastIndexOf("#");
        if (index != -1) {
            this.buildNameFragment = uri.substring(index + 1);
            log.infof("Found buildName %s within SCM URL %s", buildNameFragment, uri);
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<TagMapping> getTagMapping() {
        return tagMapping;
    }

    public void setTagMapping(List<TagMapping> tagMapping) {
        this.tagMapping = tagMapping;
    }

    @JsonProperty("private")
    public boolean isPrivateRepo() {
        return privateRepo;
    }

    public RepositoryInfo setPrivateRepo(boolean privateRepo) {
        this.privateRepo = privateRepo;
        return this;
    }

    public String getBuildNameFragment() {
        return buildNameFragment;
    }

    public RepositoryInfo setBuildNameFragment(String buildNameFragment) {
        this.buildNameFragment = buildNameFragment;
        return this;
    }

    @JsonIgnore
    public String getUriWithoutFragment() {
        return uri.replace("#" + buildNameFragment, "");
    }

    public void setUriWithoutFragment(String s) {
        // Noop to avoid bean issues.
    }

    @Override
    public String toString() {
        return "RepositoryInfo{" + "uri='" + uri + '\'' + ", path='" + path + '\'' + '}';
    }
}
