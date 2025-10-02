package io.quarkus.domino.recipes.build;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildRecipeInfo {

    /**
     * If this is true then the version will be explicitly set before doing the build
     */
    boolean enforceVersion;
    List<String> additionalArgs = new ArrayList<>();

    /**
     * Parameters that are used instead of the default build command line
     */
    List<String> alternativeArgs = new ArrayList<>();

    /**
     * Additional repositories to use in the rebuild.
     */
    List<String> repositories = new ArrayList<>();
    String toolVersion;
    String javaVersion;

    String preBuildScript;

    String postBuildScript;

    boolean disableSubmodules;

    int additionalMemory;

    List<AdditionalDownload> additionalDownloads = new ArrayList<>();

    List<String> disabledPlugins;

    boolean runTests;

    /**
     * This contains a map of additional build instructions for builds referenced
     * by a scm.yaml with a hash fragment. These build instructions are not combined
     * with the parent so any common instructions must be duplicated.
     */
    Map<String, BuildRecipeInfo> additionalBuilds;

    List<String> allowedDifferences = new ArrayList<>();

    String tool;

    public List<String> getAdditionalArgs() {
        return additionalArgs;
    }

    public BuildRecipeInfo setAdditionalArgs(List<String> additionalArgs) {
        this.additionalArgs = additionalArgs;
        return this;
    }

    public boolean isEnforceVersion() {
        return enforceVersion;
    }

    public BuildRecipeInfo setEnforceVersion(boolean enforceVersion) {
        this.enforceVersion = enforceVersion;
        return this;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    public BuildRecipeInfo setRepositories(List<String> repositories) {
        this.repositories = repositories;
        return this;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public BuildRecipeInfo setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
        return this;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public BuildRecipeInfo setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
        return this;
    }

    public String getPreBuildScript() {
        return preBuildScript;
    }

    public BuildRecipeInfo setPreBuildScript(String preBuildScript) {
        this.preBuildScript = preBuildScript;
        return this;
    }

    public String getPostBuildScript() {
        return postBuildScript;
    }

    public BuildRecipeInfo setPostBuildScript(String postBuildScript) {
        this.postBuildScript = postBuildScript;
        return this;
    }

    public List<AdditionalDownload> getAdditionalDownloads() {
        return additionalDownloads;
    }

    public BuildRecipeInfo setAdditionalDownloads(List<AdditionalDownload> additionalDownloads) {
        this.additionalDownloads = additionalDownloads;
        return this;
    }

    public boolean isDisableSubmodules() {
        return disableSubmodules;
    }

    public BuildRecipeInfo setDisableSubmodules(boolean disableSubmodules) {
        this.disableSubmodules = disableSubmodules;
        return this;
    }

    public List<String> getAlternativeArgs() {
        return alternativeArgs;
    }

    public BuildRecipeInfo setAlternativeArgs(List<String> alternativeArgs) {
        this.alternativeArgs = alternativeArgs;
        return this;
    }

    public int getAdditionalMemory() {
        return additionalMemory;
    }

    public BuildRecipeInfo setAdditionalMemory(int additionalMemory) {
        this.additionalMemory = additionalMemory;
        return this;
    }

    public boolean isRunTests() {
        return runTests;
    }

    public BuildRecipeInfo setRunTests(boolean runTests) {
        this.runTests = runTests;
        return this;
    }

    public Map<String, BuildRecipeInfo> getAdditionalBuilds() {
        return additionalBuilds;
    }

    public BuildRecipeInfo setAdditionalBuilds(Map<String, BuildRecipeInfo> additionalBuilds) {
        this.additionalBuilds = additionalBuilds;
        return this;
    }

    public List<String> getAllowedDifferences() {
        return allowedDifferences;
    }

    public BuildRecipeInfo setAllowedDifferences(List<String> allowedDifferences) {
        this.allowedDifferences = allowedDifferences;
        return this;
    }

    public List<String> getDisabledPlugins() {
        return disabledPlugins;
    }

    public BuildRecipeInfo setDisabledPlugins(List<String> disabledPlugins) {
        this.disabledPlugins = disabledPlugins;
        return this;
    }

    public String getTool() {
        return tool;
    }

    public BuildRecipeInfo setTool(String tool) {
        this.tool = tool;
        return this;
    }

    @Override
    public String toString() {
        return "BuildRecipeInfo{" +
                "enforceVersion=" + enforceVersion +
                ", additionalArgs=" + additionalArgs +
                ", alternativeArgs=" + alternativeArgs +
                ", repositories=" + repositories +
                ", toolVersion='" + toolVersion + '\'' +
                ", javaVersion='" + javaVersion + '\'' +
                ", preBuildScript='" + preBuildScript + '\'' +
                ", postBuildScript='" + postBuildScript + '\'' +
                ", disableSubmodules=" + disableSubmodules +
                ", additionalMemory=" + additionalMemory +
                ", additionalDownloads=" + additionalDownloads +
                ", additionalBuilds=" + additionalBuilds +
                ", allowedDifferences=" + allowedDifferences +
                ", disabledPlugins=" + disabledPlugins +
                '}';
    }
}
