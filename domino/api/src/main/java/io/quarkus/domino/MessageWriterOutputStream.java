package io.quarkus.domino;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Output stream delegating to {@link MessageWriter}
 */
public class MessageWriterOutputStream extends OutputStream {

    /**
     * The internal buffer where data is stored.
     */
    protected final byte buf[];

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range {@code 0} through {@code buf.length}; elements
     * {@code buf[0]} through {@code buf[count-1]} contain valid
     * byte data.
     */
    protected int count;

    private final MessageWriter log;

    /**
     * Creates a new buffered output stream to write data to the
     * specified message writer.
     *
     * @param log message writer.
     */
    public MessageWriterOutputStream(MessageWriter log) {
        this(log, 8192);
    }

    /**
     * Creates a new buffered output stream to write data to the
     * specified message writer with the specified buffer
     * size.
     *
     * @param log the underlying output stream.
     * @param size the buffer size.
     * @throws IllegalArgumentException if size &lt;= 0.
     */
    public MessageWriterOutputStream(MessageWriter log, int size) {
        this.log = Objects.requireNonNull(log);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    /** Flush the internal buffer */
    private void flushBuffer() throws IOException {
        if (count > 0) {
            log(new String(buf, 0, count));
            count = 0;
        }
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param b the byte to be written.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte) b;
    }

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this buffered output stream.
     *
     * <p>
     * Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed. If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream. Thus redundant
     * {@code BufferedOutputStream}s will not copy data unnecessarily.
     *
     * @param b the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized void write(byte b[], int off, int len) throws IOException {
        if (len >= buf.length) {
            /*
             * If the request length exceeds the size of the output buffer,
             * flush the output buffer and then write the data directly.
             * In this way buffered streams will cascade harmlessly.
             */
            flushBuffer();
            log(new String(b, off, len));
            return;
        }
        if (len > buf.length - count) {
            flushBuffer();
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    /**
     * Flushes this buffered output stream. This forces any buffered
     * output bytes to be written out to the message writer.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized void flush() throws IOException {
        flushBuffer();
    }

    private void log(String msg) {
        var ls = System.lineSeparator();
        var from = 0;
        var i = msg.indexOf(ls);
        while (i >= 0) {
            println(msg.substring(from, i));
            from = i + ls.length();
            i = msg.indexOf(ls, from);
        }
        if (from < msg.length()) {
            if (msg.endsWith(ls)) {
                println(msg.substring(from, msg.length() - ls.length()));
            } else {
                println(msg.substring(from));
            }
        }
    }

    private void println(String line) {
        log.info(line);
    }
}
