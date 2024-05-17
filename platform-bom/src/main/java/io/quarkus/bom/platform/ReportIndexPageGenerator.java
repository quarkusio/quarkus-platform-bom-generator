package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.FileReportWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ReportIndexPageGenerator extends FileReportWriter implements AutoCloseable {

    private static final String[] listBackground = new String[] { "background-color:#EBF4FA",
            "background-color:#FFFFFF" };

    private URL mainBomUrl;
    private DecomposedBom mainBom;
    private Path mainBomReleasesHtml;

    private final Collection<MemberData> memberData = new ConcurrentLinkedDeque<>();

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

        int backgroundIndex = 1;
        if (mainBomUrl != null) {
            openTag("table");
            openTag("tr", listBackground[backgroundIndex]);
            writeTag("td", "text-align:left;font-weight:bold;color:gray", mainBom.bomArtifact());
            writeTag("td", "text-align:left", generateAnchor(mainBomUrl.toExternalForm(), "generated"));
            writeTag("td", "text-align:left",
                    generateAnchor(mainBomReleasesHtml.toUri().toURL().toExternalForm(), "decomposed"));
            closeTag("tr");
            closeTag("table");
        }

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "Extension BOMs");
        for (var member : memberData) {
            openTag("tr", listBackground[backgroundIndex ^= 1]);
            writeTag("td", "text-align:left;font-weight:bold;color:gray", member.toBom.bomArtifact());
            writeTag("td", "text-align:left", generateAnchor(member.mainUrl.toExternalForm(), "original"));
            writeTag("td", "text-align:left",
                    generateAnchor(member.mainReleasesHtml.toUri().toURL().toExternalForm(), "decomposed"));
            writeTag("td", "text-align:left", generateAnchor(member.toUrl.toExternalForm(), "generated"));
            writeTag("td", "text-align:left",
                    generateAnchor(member.toReleasesHtml.toUri().toURL().toExternalForm(), "decomposed"));
            writeTag("td", "text-align:left", generateAnchor(member.diffHtml.toUri().toURL().toExternalForm(), "diff"));
            closeTag("tr");
        }
        closeTag("table");
    }

    public void universalBom(URL mainUrl, DecomposedBom decomposed, Path releasesHtml) {
        this.mainBomUrl = mainUrl;
        this.mainBom = decomposed;
        this.mainBomReleasesHtml = releasesHtml;
    }

    public void bomReport(URL mainUrl, URL toUrl, DecomposedBom toBom, Path mainReleasesHtml, Path toReleasesHtml,
            Path diffHtml) {
        memberData.add(new MemberData(mainUrl, toUrl, toBom, mainReleasesHtml, toReleasesHtml, diffHtml));
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

    private static class MemberData {
        final URL mainUrl;
        final URL toUrl;
        final DecomposedBom toBom;
        final Path mainReleasesHtml;
        final Path toReleasesHtml;
        final Path diffHtml;

        public MemberData(URL mainUrl, URL toUrl, DecomposedBom toBom, Path mainReleasesHtml, Path toReleasesHtml,
                Path diffHtml) {
            this.mainUrl = mainUrl;
            this.toUrl = toUrl;
            this.toBom = toBom;
            this.mainReleasesHtml = mainReleasesHtml;
            this.toReleasesHtml = toReleasesHtml;
            this.diffHtml = diffHtml;
        }
    }
}
