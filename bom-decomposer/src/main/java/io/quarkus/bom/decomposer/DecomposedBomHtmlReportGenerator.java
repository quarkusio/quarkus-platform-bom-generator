package io.quarkus.bom.decomposer;

import io.quarkus.bom.decomposer.ProjectDependency.UpdateStatus;
import io.quarkus.domino.scm.ScmRepository;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;

public class DecomposedBomHtmlReportGenerator extends DecomposedBomReportFileWriter {

    private static final String[] listBackground = new String[] { "background-color:#EBF4FA",
            "background-color:#FFFFFF" };

    public static HtmlWriterBuilder builder(String file) {
        return new DecomposedBomHtmlReportGenerator(file).new HtmlWriterBuilder();
    }

    public static HtmlWriterBuilder builder(Path file) {
        return new DecomposedBomHtmlReportGenerator(file).new HtmlWriterBuilder();
    }

    public class HtmlWriterBuilder {

        private HtmlWriterBuilder() {
        }

        public HtmlWriterBuilder indentChars(int indent) {
            indentChars(indent);
            return this;
        }

        public HtmlWriterBuilder skipOriginsWithSingleRelease() {
            skipOriginsWithSingleRelease = true;
            return this;
        }

        public DecomposedBomHtmlReportGenerator build() {
            return DecomposedBomHtmlReportGenerator.this;
        }
    }

    private static String RED = "Red";
    private static String GREEN = "Green";
    private static String BLUE = "Blue";

    private int releaseOriginsTotal;
    private int releaseVersionsTotal;
    private int artifactsTotal;
    private int releaseOriginsWithConflictsTotal;
    private int resolvableConflictsTotal;
    private int unresolvableConflictsTotal;

    private boolean skipOriginsWithSingleRelease;

    private Map<String, Map<String, ProjectDependency>> allDeps = new HashMap<>();
    private int originReleaseVersions;
    private List<ArtifactVersion> releaseVersions = new ArrayList<>();

    private DecomposedBomHtmlReportGenerator(String file) {
        super(file);
    }

    private DecomposedBomHtmlReportGenerator(Path file) {
        super(file);
    }

    @Override
    protected void writeStartBom(BufferedWriter writer, Artifact bomArtifact) throws IOException {
        writeLine("<!DOCTYPE html>");
        openTag("html");

        openTag("head");
        offsetLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");

        openTag("style");
        writeLine(".accordion {\n" +
                "  background-color: #eee;\n" +
                "  color: #444;\n" +
                "  cursor: pointer;\n" +
                "  padding: 18px;\n" +
                "  width: 100%;\n" +
                "  border: none;\n" +
                "  text-align: left;\n" +
                "  outline: none;\n" +
                "  font-size: 15px;\n" +
                "  transition: 0.4s;\n" +
                "}");
        writeLine(".active, .accordion:hover {\n" +
                "  background-color: #ccc; \n" +
                "}");
        writeLine(".panel {\n" +
                "  padding: 0 18px;\n" +
                "  display: none;\n" +
                "  background-color: white;\n" +
                "  overflow: hidden;\n" +
                "}");
        closeTag("style");
        closeTag("head");

        openTag("body");

        writeTag("h1", "Multi Module Project Releases Report");
        writeTag("p", "Includes multi module project releases detected among the managed dependencies of the BOM");
        if (skipOriginsWithSingleRelease) {
            writeTag("p", "font-weight:bold;color:" + RED, "Release origins with a single release were filtered out");
        }

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "BOM:");
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "groupId:");
        writeTag("td", "font-weight:bold", bomArtifact.getGroupId());
        closeTag("tr");
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "artifactId:");
        writeTag("td", "font-weight:bold", bomArtifact.getArtifactId());
        closeTag("tr");
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "version:");
        writeTag("td", "font-weight:bold", bomArtifact.getVersion());
        closeTag("tr");
        closeTag("table");

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "Colors highlighting the versions:");
        openTag("tr");
        writeTag("th", "text-align:left;color:" + BLUE, "Blue");
        writeTag("td",
                "- version of the artifact found in the BOM which is either the preferred version or an older version for which the preferred version is not available");
        closeTag("tr");
        openTag("tr");
        writeTag("th", "text-align:left;color:" + RED, "Red");
        writeTag("td",
                "- old version of the artifact found in the BOM for which the preferred version is available in the Maven repository");
        closeTag("tr");
        openTag("tr");
        writeTag("th", "text-align:left;color:" + GREEN, "Green");
        writeTag("td", "- the preferred version of the artifact found to be available in the Maven repository");
        closeTag("tr");
        closeTag("table");

        openTag("p");
        closeTag("p");
    }

    @Override
    protected boolean writeStartReleaseOrigin(BufferedWriter writer, ScmRepository releaseOrigin, int versions)
            throws IOException {
        originReleaseVersions = versions;
        if (versions > 1) {
            releaseOriginsWithConflictsTotal++;
        }
        return versions > 1 || !skipOriginsWithSingleRelease;
    }

    @Override
    protected void writeEndReleaseOrigin(BufferedWriter writer, ScmRepository releaseOrigin) throws IOException {
        offsetLine("<button class=\"accordion\">" + releaseOrigin
                + (originReleaseVersions > 1 ? " (" + originReleaseVersions + ")" : "") + "</button>");
        offsetLine("<div class=\"panel\">");

        Collections.sort(releaseVersions);
        final List<String> stringVersions = releaseVersions.stream().map(Object::toString).collect(Collectors.toList());

        openTag("table");
        int i = 1;

        for (String releaseVersionStr : stringVersions) {
            final Map<String, ProjectDependency> releaseDeps = allDeps.get(releaseVersionStr);
            final List<String> sortedKeys = new ArrayList<>(releaseDeps.keySet());
            Collections.sort(sortedKeys);
            for (String key : sortedKeys) {
                openTag("tr");
                writeTag("td", i++ + ")");
                final ProjectDependency dep = releaseDeps.get(key);
                writeTag("td", dep.artifact());
                for (int j = 0; j < stringVersions.size(); ++j) {
                    final String version = stringVersions.get(j);
                    if (dep.releaseId().getValue().equals(version)) {
                        writeTag("td",
                                !dep.isUpdateAvailable() || j == stringVersions.size() - 1 ? "color:" + BLUE : "color:" + RED,
                                version);
                    } else if (dep.isUpdateAvailable()
                            && dep.availableUpdate().releaseId().getValue().equals(version)) {
                        writeTag("td", "color:" + GREEN, version);
                    } else {
                        emptyTag("td");
                    }
                }
                if (dep.updateStatus() != UpdateStatus.UNKNOWN && originReleaseVersions > 1) {
                    if (dep.updateStatus() == UpdateStatus.AVAILABLE) {
                        ++resolvableConflictsTotal;
                    } else {
                        ++unresolvableConflictsTotal;
                    }
                }

                closeTag("tr");
            }
        }

        closeTag("table");

        offsetLine("</div>");

        ++releaseOriginsTotal;
        allDeps.clear();
        releaseVersionsTotal += releaseVersions.size();
        releaseVersions.clear();
    }

    @Override
    protected void writeProjectRelease(BufferedWriter writer, ProjectRelease release) throws IOException {
        final Collection<ProjectDependency> deps = release.dependencies();
        releaseVersions.add(new DefaultArtifactVersion(release.id().getValue()));
        final Map<String, ProjectDependency> releaseDeps = new HashMap<>(deps.size());
        allDeps.put(release.id().getValue(), releaseDeps);
        for (ProjectDependency dep : deps) {
            releaseDeps.put(dep.key().toString(), dep);
        }
        artifactsTotal += deps.size();
    }

    @Override
    protected void writeEndBom(BufferedWriter writer) throws IOException {

        int backgroundIndex = 0;

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "Total:");
        openTag("tr", listBackground[backgroundIndex ^= 1]);
        writeTag("td", "text-align:left;", "Resolvable version conflicts:");
        writeTag("td", "text-align:right;", resolvableConflictsTotal);
        closeTag("tr");
        openTag("tr", listBackground[backgroundIndex ^= 1]);
        writeTag("td", "text-align:left;", "Unresolvable version conflicts:");
        writeTag("td", "text-align:right;", unresolvableConflictsTotal);
        closeTag("tr");
        openTag("tr", listBackground[backgroundIndex ^= 1]);
        writeTag("td", "text-align:left;", "Release origins:");
        writeTag("td", "text-align:right;", releaseOriginsTotal);
        closeTag("tr");
        openTag("tr", listBackground[backgroundIndex ^= 1]);
        writeTag("td", "text-align:left;", "Release origins with conflicts:");
        writeTag("td", "text-align:right;", releaseOriginsWithConflictsTotal);
        closeTag("tr");
        openTag("tr", listBackground[backgroundIndex ^= 1]);
        writeTag("td", "text-align:left;", "Release versions:");
        writeTag("td", "text-align:right;", releaseVersionsTotal);
        closeTag("tr");
        openTag("tr", listBackground[backgroundIndex ^= 1]);
        writeTag("td", "text-align:left;", "Artifacts:");
        writeTag("td", "text-align:right;", artifactsTotal);
        closeTag("tr");
        closeTag("table");

        openTag("script");
        writeLine("var acc = document.getElementsByClassName(\"accordion\");\n" +
                "var i;\n" +
                "for (i = 0; i < acc.length; i++) {\n" +
                "  acc[i].addEventListener(\"click\", function() {\n" +
                "    this.classList.toggle(\"active\");\n" +
                "    var panel = this.nextElementSibling;\n" +
                "    if (panel.style.display === \"block\") {\n" +
                "      panel.style.display = \"none\";\n" +
                "    } else {\n" +
                "      panel.style.display = \"block\";\n" +
                "    }\n" +
                "  });\n" +
                "}");
        closeTag("script");

        closeTag("body");
        closeTag("html");
    }
}
