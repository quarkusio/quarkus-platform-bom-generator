package io.quarkus.bom.task;

import java.util.Collection;

public interface PlatformGenTaskScheduler {

    boolean IS_PARALLEL_DEFAULT = !Boolean.getBoolean("sequentialTaskScheduler");

    static PlatformGenTaskScheduler getInstance() {
        return IS_PARALLEL_DEFAULT ? new ParallelTaskScheduler() : new SequentialTaskScheduler();
    }

    void schedule(PlatformGenTask task) throws Exception;

    void addFinializingTask(PlatformGenTask task) throws Exception;

    void waitForCompletion() throws Exception;

    boolean hasErrors();

    Collection<Exception> getErrors();
}
