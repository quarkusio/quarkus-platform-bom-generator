package io.quarkus.bom.platform;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.FileReportWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ReportIndexPageGenerator extends FileReportWriter implements AutoCloseable {

    private static final String[] listBackground = new String[] { "background-color:#EBF4FA",
            "background-color:#FFFFFF" };

    private Path mainBomPath;
    private DecomposedBom mainBom;
    private Path mainBomReleasesHtml;

    private final Collection<MemberData> memberData = new ConcurrentLinkedDeque<>();

    public ReportIndexPageGenerator(Path file) throws IOException {
        super(file);
        initHtmlBody();
    }

    private String relativize(Path target) {
        return reportFile.getParent().relativize(target).toString();
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
        if (mainBomPath != null) {
            openTag("table");
            openTag("tr", listBackground[backgroundIndex]);
            writeTag("td", "text-align:left;font-weight:bold;color:gray", mainBom.bomArtifact());
            writeTag("td", "text-align:left", generateAnchor(relativize(mainBomPath), "generated"));
            writeTag("td", "text-align:left",
                    generateAnchor(relativize(mainBomReleasesHtml), "decomposed"));
            closeTag("tr");
            closeTag("table");
        }

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "Extension BOMs");
        for (var member : memberData) {
            openTag("tr", listBackground[backgroundIndex ^= 1]);
            writeTag("td", "text-align:left;font-weight:bold;color:gray", member.toBom.bomArtifact());
            writeTag("td", "text-align:left", generateAnchor(relativize(member.mainPath), "original"));
            writeTag("td", "text-align:left",
                    generateAnchor(relativize(member.mainReleasesHtml), "decomposed"));
            writeTag("td", "text-align:left", generateAnchor(relativize(member.toPath), "generated"));
            writeTag("td", "text-align:left",
                    generateAnchor(relativize(member.toReleasesHtml), "decomposed"));
            writeTag("td", "text-align:left", generateAnchor(relativize(member.diffHtml), "diff"));
            closeTag("tr");
        }
        closeTag("table");
    }

    public void universalBom(Path mainBomPath, DecomposedBom decomposed, Path releasesHtml) {
        this.mainBomPath = mainBomPath;
        this.mainBom = decomposed;
        this.mainBomReleasesHtml = releasesHtml;
    }

    public void bomReport(Path mainPath, Path toPath, DecomposedBom toBom, Path mainReleasesHtml, Path toReleasesHtml,
            Path diffHtml) {
        memberData.add(new MemberData(mainPath, toPath, toBom, mainReleasesHtml, toReleasesHtml, diffHtml));
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
        final Path mainPath;
        final Path toPath;
        final DecomposedBom toBom;
        final Path mainReleasesHtml;
        final Path toReleasesHtml;
        final Path diffHtml;

        public MemberData(Path mainPath, Path toPath, DecomposedBom toBom, Path mainReleasesHtml, Path toReleasesHtml,
                Path diffHtml) {
            this.mainPath = mainPath;
            this.toPath = toPath;
            this.toBom = toBom;
            this.mainReleasesHtml = mainReleasesHtml;
            this.toReleasesHtml = toReleasesHtml;
            this.diffHtml = diffHtml;
        }
    }
}
