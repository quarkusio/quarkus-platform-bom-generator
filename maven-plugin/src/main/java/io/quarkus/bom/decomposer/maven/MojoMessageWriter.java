package io.quarkus.bom.decomposer.maven;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.Objects;
import org.apache.maven.plugin.logging.Log;

public class MojoMessageWriter implements MessageWriter {

    private final Log log;

    public MojoMessageWriter(Log log) {
        this.log = Objects.requireNonNull(log);
    }

    @Override
    public void info(String msg) {
        log.info(msg);
    }

    @Override
    public void error(String msg) {
        log.error(msg);
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        log.debug(msg);
    }

    @Override
    public void warn(String msg) {
        log.warn(msg);
    }
}
