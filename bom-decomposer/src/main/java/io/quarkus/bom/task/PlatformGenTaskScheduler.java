package io.quarkus.bom.task;

import io.quarkus.bom.decomposer.BomDecomposer;
import java.util.Collection;

public interface PlatformGenTaskScheduler {

    static PlatformGenTaskScheduler getInstance() {
        return BomDecomposer.isParallelProcessing() ? new ConcurrentTaskScheduler() : new SequentialTaskScheduler();
    }

    void schedule(PlatformGenTask task) throws Exception;

    void addFinializingTask(PlatformGenTask task) throws Exception;

    void waitForCompletion() throws Exception;

    boolean hasErrors();

    Collection<Exception> getErrors();
}
