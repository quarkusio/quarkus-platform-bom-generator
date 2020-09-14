package io.quarkus.bom.decomposer;

import java.io.PrintStream;

public class DefaultMessageWriter implements MessageWriter {

    protected final PrintStream out;
    protected boolean debug;

    public DefaultMessageWriter() {
        this(System.out);
    }

    public DefaultMessageWriter(PrintStream out) {
        this.out = out;
    }

    public DefaultMessageWriter setDebugEnabled(boolean debugEnabled) {
        this.debug = debugEnabled;
        return this;
    }

    @Override
    public boolean debugEnabled() {
        return debug;
    }

    @Override
    public void info(Object msg) {
        out.println(msg);
    }

    @Override
    public void error(Object msg) {
        out.println(msg);
    }

    @Override
    public void debug(Object msg) {
        if (!debugEnabled()) {
            return;
        }
        out.println(msg);
    }

    @Override
    public void warn(Object msg) {
        out.println("WARN: " + msg);
    }
}
