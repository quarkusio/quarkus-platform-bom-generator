package io.quarkus.bom.decomposer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class FileReportWriter {

    protected final Path reportFile;
    private BufferedWriter writer;

    private int tagDepth;
    private int indentChars = 4;
    private StringBuilder buf;
    private boolean closed;

    public FileReportWriter(String name) {
        this(Paths.get(name));
    }

    public FileReportWriter(Path p) {
        Objects.requireNonNull(p);
        reportFile = p;
    }

    protected boolean isClosed() {
        return closed;
    }

    protected void indentChars(int indentChars) {
        this.indentChars = indentChars;
    }

    protected BufferedWriter initWriter() throws IOException {
        if (!Files.exists(reportFile)) {
            final Path parentDir = reportFile.isAbsolute() ? reportFile.getParent()
                    : reportFile.normalize().toAbsolutePath().getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
        }
        return writer = Files.newBufferedWriter(reportFile);
    }

    protected BufferedWriter writer() throws IOException {
        return closed ? null : (writer == null ? writer = initWriter() : writer);
    }

    protected void writeLine(Object line) throws IOException {
        append(line);
        newLine();
    }

    protected void newLine() throws IOException {
        writer().newLine();
    }

    protected void append(Object line) throws IOException {
        writer().write(line.toString());
    }

    protected StringBuilder buf() {
        if (buf == null) {
            buf = new StringBuilder();
        } else {
            buf.setLength(0);
        }
        return buf;
    }

    protected void writeTag(String name, Object value) throws IOException {
        writeTag(name, null, value);
    }

    protected void writeTag(String name, String style, Object value) throws IOException {
        offset();
        final StringBuilder buf = buf();
        buf.append('<').append(name);
        if (style != null) {
            buf.append(" style=\"").append(style).append("\"");
        }
        buf.append('>').append(value).append("</").append(name).append('>');
        writeLine(buf);
    }

    protected void writeAnchor(String url, String text) throws IOException {
        offset();
        writeLine(generateAnchor(url, text));
    }

    protected String generateAnchor(String url, String text) {
        final StringBuilder buf = buf();
        buf.append("<a href=\"").append(url).append("\">").append(text).append("</a>");
        return buf.toString();
    }

    protected void openTag(String name) throws IOException {
        openTag(name, null);
    }

    protected void openTag(String name, String style) throws IOException {
        offset();
        final StringBuilder buf = buf();
        buf.append('<').append(name);
        if (style != null) {
            buf.append(" style=\"").append(style).append("\"");
        }
        buf.append('>');
        writeLine(buf.toString());
        ++tagDepth;
    }

    protected void closeTag(String name) throws IOException {
        --tagDepth;
        offset();
        final StringBuilder buf = buf();
        buf.append("</").append(name).append('>');
        writeLine(buf.toString());
    }

    protected void emptyTag(String name) throws IOException {
        offset();
        final StringBuilder buf = buf();
        buf.append("<").append(name).append("/>");
        writeLine(buf.toString());
    }

    protected void offsetLine(String line) throws IOException {
        offset();
        writeLine(line);
    }

    protected void offset() throws IOException {
        final StringBuilder buf = buf();
        for (int i = 0; i < tagDepth * indentChars; ++i) {
            buf.append(' ');
        }
        append(buf);
    }

    protected void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
            }
            writer = null;
        }
        closed = true;
    }
}
