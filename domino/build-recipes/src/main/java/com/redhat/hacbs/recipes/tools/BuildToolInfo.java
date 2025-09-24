package com.redhat.hacbs.recipes.tools;

public class BuildToolInfo {

    private String version;
    private String releaseDate;
    private String minJdkVersion;
    private String maxJdkVersion;

    public String getVersion() {
        return version;
    }

    public BuildToolInfo setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public BuildToolInfo setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
        return this;
    }

    public String getMinJdkVersion() {
        return minJdkVersion;
    }

    public BuildToolInfo setMinJdkVersion(String minJdkVersion) {
        this.minJdkVersion = minJdkVersion;
        return this;
    }

    public String getMaxJdkVersion() {
        return maxJdkVersion;
    }

    public BuildToolInfo setMaxJdkVersion(String maxJdkVersion) {
        this.maxJdkVersion = maxJdkVersion;
        return this;
    }

    @Override
    public String toString() {
        return "BuildToolInfo{" +
                "version='" + version + '\'' +
                ", releaseDate='" + releaseDate + '\'' +
                ", minJdkVersion='" + minJdkVersion + '\'' +
                ", maxJdkVersion='" + maxJdkVersion + '\'' +
                '}';
    }
}
