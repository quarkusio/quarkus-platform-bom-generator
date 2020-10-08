package io.quarkus.bom.decomposer;

public interface MessageWriter {

    default void info(String format, Object... args) {
        info(String.format(format, args));
    }

    void info(Object msg);

    default void error(String format, Object... args) {
        error(String.format(format, args));
    }

    void error(Object msg);

    boolean debugEnabled();

    default void debug(String format, Object... args) {
        if (!debugEnabled()) {
            return;
        }
        debug(String.format(format, args));
    }

    void debug(Object msg);

    default void warn(String format, Object... args) {
        warn(String.format(format, args));
    }

    void warn(Object msg);
}
