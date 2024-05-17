package io.quarkus.bom.task;

import java.util.Collection;
import java.util.List;

public class SequentialTaskScheduler implements PlatformGenTaskScheduler {

    @Override
    public void schedule(PlatformGenTask task) throws Exception {
        task.run();
    }

    @Override
    public void addFinializingTask(PlatformGenTask task) throws Exception {
        task.run();
    }

    @Override
    public void waitForCompletion() {
    }

    @Override
    public boolean hasErrors() {
        return false;
    }

    @Override
    public Collection<Exception> getErrors() {
        return List.of();
    }
}
