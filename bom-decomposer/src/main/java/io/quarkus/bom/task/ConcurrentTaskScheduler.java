package io.quarkus.bom.task;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Phaser;

public class ConcurrentTaskScheduler implements PlatformGenTaskScheduler {

    private final Phaser phaser = new Phaser(1);
    private final Deque<Exception> errors = new ConcurrentLinkedDeque<>();
    private final Deque<PlatformGenTask> finalizingTasks = new ConcurrentLinkedDeque<>();

    @Override
    public void schedule(PlatformGenTask task) {
        phaser.register();
        CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                phaser.arriveAndDeregister();
            }
        });
    }

    @Override
    public void addFinializingTask(PlatformGenTask task) {
        finalizingTasks.add(task);
    }

    @Override
    public void waitForCompletion() throws Exception {
        phaser.arriveAndAwaitAdvance();
        for (var t : finalizingTasks) {
            t.run();
        }
    }

    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public Collection<Exception> getErrors() {
        return errors;
    }
}
