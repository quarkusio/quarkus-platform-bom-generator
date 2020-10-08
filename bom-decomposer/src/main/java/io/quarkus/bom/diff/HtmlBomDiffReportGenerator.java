package io.quarkus.bom.diff;

import io.quarkus.bom.decomposer.FileReportWriter;
import io.quarkus.bom.diff.BomDiff.VersionChange;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

public class HtmlBomDiffReportGenerator extends FileReportWriter implements BomDiffReportGenerator {

    private static final String[] listBackground = new String[] { "background-color:#EBF4FA",
            "background-color:#FFFFFF" };

    public static Config config(String name) {
        return new HtmlBomDiffReportGenerator(name).new Config();
    }

    public static Config config(Path reportFile) {
        return new HtmlBomDiffReportGenerator(reportFile).new Config();
    }

    public class Config {

        private Config() {
        }

        public void report(BomDiff bomDiff) {
            HtmlBomDiffReportGenerator.this.report(bomDiff);
        }
    }

    private NumberFormat numberFormat;

    private HtmlBomDiffReportGenerator(String name) {
        super(name);
    }

    private HtmlBomDiffReportGenerator(Path path) {
        super(path);
    }

    @Override
    public void report(BomDiff bomDiff) {
        try {
            generateHtml(bomDiff);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate HTML report", e);
        } finally {
            close();
        }
    }

    protected void generateHtml(BomDiff bomDiff) throws IOException {
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

        writeTag("h1", "Managed Dependencies Comparison Report");

        generateBody(bomDiff);

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

    private void generateBody(BomDiff bomDiff) throws IOException {

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "BOM");
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "groupId:");
        writeTag("td", "font-weight:bold", bomDiff.mainBom().getGroupId());
        closeTag("tr");
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "artifactId:");
        writeTag("td", "font-weight:bold", bomDiff.mainBom().getArtifactId());
        closeTag("tr");
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "version:");
        writeTag("td", "font-weight:bold", bomDiff.mainBom().getVersion());
        closeTag("tr");
        if (!bomDiff.mainBom().toString().equals(bomDiff.mainSource())) {
            openTag("tr");
            writeTag("td", "text-align:left;font-weight:bold;color:gray", "source:");
            writeTag("td", "font-weight:bold", bomDiff.mainSource());
            closeTag("tr");
        }
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "managed dependencies:");
        writeTag("td", /* "font-weight:bold", */ bomDiff.mainBomSize());
        closeTag("tr");
        closeTag("table");

        writeTag("p", "");
        openTag("table");
        writeTag("caption", "text-align:left;font-weight:bold", "compared to");
        if (!bomDiff.toBom().equals(bomDiff.mainBom())) {
            openTag("tr");
            writeTag("td", "text-align:left;font-weight:bold;color:gray", "groupId:");
            writeTag("td", "font-weight:bold", bomDiff.mainBom().getGroupId());
            closeTag("tr");
            openTag("tr");
            writeTag("td", "text-align:left;font-weight:bold;color:gray", "artifactId:");
            writeTag("td", "font-weight:bold", bomDiff.mainBom().getArtifactId());
            closeTag("tr");
            openTag("tr");
            writeTag("td", "text-align:left;font-weight:bold;color:gray", "version:");
            writeTag("td", "font-weight:bold", bomDiff.mainBom().getVersion());
            closeTag("tr");
        }
        if (!bomDiff.toBom().toString().equals(bomDiff.toSource())) {
            openTag("tr");
            writeTag("td", "text-align:left;font-weight:bold;color:gray", "source:");
            writeTag("td", "font-weight:bold", bomDiff.toSource());
            closeTag("tr");
        }
        openTag("tr");
        writeTag("td", "text-align:left;font-weight:bold;color:gray", "managed dependencies:");
        writeTag("td", /* "font-weight:bold", */ bomDiff.toBomSize());
        closeTag("tr");
        closeTag("table");

        if (bomDiff.hasDowngraded()) {
            versionChangeAccordion("Downgraded dependencies", bomDiff.mainBomSize(), bomDiff.downgraded());
        }
        if (bomDiff.hasUpgraded()) {
            versionChangeAccordion("Upgraded dependencies", bomDiff.mainBomSize(), bomDiff.upgraded());
        }
        if (bomDiff.hasMissing()) {
            depsListAccordion("Removed dependencies", bomDiff.mainBomSize(), bomDiff.missing(), true);
        }
        if (bomDiff.hasExtra()) {
            depsListAccordion("New dependencies", bomDiff.toBomSize(), bomDiff.extra(), false);
        }
        if (bomDiff.hasMatching()) {
            depsListAccordion("Matching dependencies", bomDiff.mainBomSize(), bomDiff.matching(), false);
        }
    }

    private void depsListAccordion(String caption, int total, List<Dependency> deps, boolean warn) throws IOException {
        accordionButton(caption, total, deps.size());
        offsetLine("<div class=\"panel\">");
        openTag("table");
        int i = 0;
        for (Dependency d : deps) {
            openTag("tr", listBackground[i ^= 1]);
            writeTag("td", gact(d.getArtifact()));
            writeTag("td", warn ? "color:red" : "color:green", d.getArtifact().getVersion());
            //writeTag("td", warn ? "color:red" : "color:green", warn ? "&#9888" : "&#9745");
            closeTag("tr");
        }
        closeTag("table");
        offsetLine("</div>");
    }

    private void versionChangeAccordion(final String caption, int total, List<VersionChange> changes)
            throws IOException {
        accordionButton(caption, total, changes.size());
        offsetLine("<div class=\"panel\">");
        openTag("table");
        int i = 0;
        for (VersionChange d : changes) {
            openTag("tr", listBackground[i ^= 1]);
            writeTag("td", gact(d.from().getArtifact()));
            writeTag("td", d.from().getArtifact().getVersion());
            writeTag("td", d.upgrade() ? "color:green" : "color:red", "&#8702");
            writeTag("td", d.upgrade() ? "color:green" : "color:red", d.to().getArtifact().getVersion());
            //writeTag("td", d.upgrade() ? "color:green" : "color:red", d.upgrade() ? "&#9745" : "&#9888");
            closeTag("tr");
        }
        closeTag("table");
        offsetLine("</div>");
    }

    private void accordionButton(final String caption, int whole, final int part) throws IOException {
        offsetLine("<button class=\"accordion\">" + caption + ": " + part + " (" + percentage(part, whole) + "%)</button>");
    }

    private String gact(Artifact a) {
        final StringBuilder buf = buf();
        buf.append(a.getGroupId()).append(':').append(a.getArtifactId());
        if (!a.getClassifier().isEmpty()) {
            buf.append(':').append(a.getClassifier());
        }
        if (!a.getExtension().equals("jar")) {
            if (a.getClassifier().isEmpty()) {
                buf.append(':');
            }
            buf.append(':').append(a.getExtension());
        }
        return buf.toString();
    }

    private String percentage(long part, long whole) {
        return format(((double) part * 100) / whole);
    }

    private String format(double d) {
        return numberFormat().format(d);
    }

    private NumberFormat numberFormat() {
        if (numberFormat == null) {
            final NumberFormat numberFormat = NumberFormat.getInstance();
            numberFormat.setMaximumFractionDigits(1);
            numberFormat.setRoundingMode(RoundingMode.HALF_DOWN);
            this.numberFormat = numberFormat;
        }
        return numberFormat;
    }

}
