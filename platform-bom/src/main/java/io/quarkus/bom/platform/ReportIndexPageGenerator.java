package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.FileReportWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReportIndexPageGenerator extends FileReportWriter implements AutoCloseable {

    private static final String[] listBackground = new String[] { "background-color:#EBF4FA",
            "background-color:#FFFFFF" };

    private List<URL> mainUrl = new ArrayList<>();
    private List<URL> toUrl = new ArrayList<>();
    private List<DecomposedBom> toBoms = new ArrayList<>();
    private List<Path> mainReleasesHtml = new ArrayList<>();
    private List<Path> toReleasesHtml = new ArrayList<>();
    private List<Path> diffHtml = new ArrayList<>();

    public ReportIndexPageGenerator(String name) throws IOException {
        super(name);
        initHtmlBody();
    }

    public ReportIndexPageGenerator(Path file) throws IOException {
        super(file);
        initHtmlBody();
    }

    private void completeHtmlBody() throws IOException {
        generateContents();
        closeTag("body");
        closeTag("html");
    }

    private void initHtmlBody() throws IOException {
        try {
            writeLine("<!DOCTYPE html>");
            openTag("html");

            openTag("head");
            offsetLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            closeTag("head");

            openTag("body");

            writeTag("h1", "Platform BOM Summary");
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void generateContents() throws IOException {
        writeTag("p", "");

        writeTag("h2", "Platform BOM");

        int i = 0;
        int backgroundIndex = 1;
        openTag("table");
        openTag("tr", listBackground[backgroundIndex]);
        writeTag("td", "text-align:left;font-weight:bold;color:gray", toBoms.get(i).bomArtifact());
        writeTag("td", "text-align:left", generateAnchor(mainUrl.get(i).toExternalForm(), "original"));
        writeTag("td", "text-align:left",
                generateAnchor(mainReleasesHtml.get(i).toUri().toURL().toExternalForm(), "decomposed"));
        writeTag("td", "text-align:left", generateAnchor(toUrl.get(i).toExternalForm(), "generated"));
        writeTag("td", "text-align:left", generateAnchor(toReleasesHtml.get(i).toUri().toURL().toExternalForm(), "decomposed"));
        writeTag("td", "text-align:left", generateAnchor(diffHtml.get(i).toUri().toURL().toExternalForm(), "diff"));
        closeTag("tr");
        closeTag("table");

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "Extension BOMs");
        while (++i < toBoms.size()) {
            openTag("tr", listBackground[backgroundIndex ^= 1]);
            writeTag("td", "text-align:left;font-weight:bold;color:gray", toBoms.get(i).bomArtifact());
            writeTag("td", "text-align:left", generateAnchor(mainUrl.get(i).toExternalForm(), "original"));
            writeTag("td", "text-align:left",
                    generateAnchor(mainReleasesHtml.get(i).toUri().toURL().toExternalForm(), "decomposed"));
            writeTag("td", "text-align:left", generateAnchor(toUrl.get(i).toExternalForm(), "generated"));
            writeTag("td", "text-align:left",
                    generateAnchor(toReleasesHtml.get(i).toUri().toURL().toExternalForm(), "decomposed"));
            writeTag("td", "text-align:left", generateAnchor(diffHtml.get(i).toUri().toURL().toExternalForm(), "diff"));
            closeTag("tr");
        }
        closeTag("table");
    }

    public void bomReport(URL mainUrl, URL toUrl, DecomposedBom toBom, Path mainReleasesHtml, Path toReleasesHtml,
            Path diffHtml) {
        this.mainUrl.add(mainUrl);
        this.toUrl.add(toUrl);
        this.toBoms.add(toBom);
        this.mainReleasesHtml.add(mainReleasesHtml);
        this.toReleasesHtml.add(toReleasesHtml);
        this.diffHtml.add(diffHtml);
    }

    @Override
    public void close() {
        if (!isClosed()) {
            try {
                completeHtmlBody();
            } catch (IOException e) {
            }
        }
        super.close();
    }
}
