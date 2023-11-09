package io.quarkus.domino.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class NodeTask<I, N, O> {

    static <I, N, O> NodeTask<I, N, O> of(I id, N node, Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func,
            Queue<TaskResult<I, N, O>> resultQueue) {
        return new NodeTask<I, N, O>(id, node, func, resultQueue);
    }

    private final I id;
    private final N node;
    private final Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func;
    private final Map<I, NodeTask<I, N, O>> dependencies = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Queue<TaskResult<I, N, O>> resultQueue;

    private CompletableFuture<TaskResult<I, N, O>> cf;

    private NodeTask(I id, N node, Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func,
            Queue<TaskResult<I, N, O>> resultQueue) {
        this.id = id;
        this.node = node;
        this.func = func;
        this.resultQueue = resultQueue;
    }

    public I getId() {
        return id;
    }

    public Collection<NodeTask<I, N, O>> getDependencies() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(dependencies.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void dependsOn(NodeTask<I, N, O> dep) {
        lock.writeLock().lock();
        try {
            dependencies.computeIfAbsent(dep.id, i -> dep);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CompletableFuture<TaskResult<I, N, O>> schedule() {
        lock.readLock().lock();
        try {
            if (cf != null) {
                return cf;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            if (cf != null) {
                return cf;
            }
            if (dependencies.isEmpty()) {
                return cf = CompletableFuture.supplyAsync(() -> {
                    var result = getResult(new ExecutionContextImpl<>(id, node), func);
                    resultQueue.add(result);
                    return result;
                });
            }
            @SuppressWarnings("unchecked")
            final CompletableFuture<TaskResult<I, N, O>>[] deps = (CompletableFuture<TaskResult<I, N, O>>[]) new CompletableFuture<?>[dependencies
                    .size()];
            int ti = 0;
            for (NodeTask<I, N, O> t : dependencies.values()) {
                deps[ti++] = t.schedule();
            }
            return cf = CompletableFuture.allOf(deps).thenApplyAsync((v) -> {
                final Map<I, TaskResult<I, N, O>> dependencyResults = new HashMap<>(deps.length);
                for (int i = 0; i < deps.length; ++i) {
                    final TaskResult<I, N, O> depResult = deps[i].getNow(null);
                    if (depResult == null) {
                        var result = new TaskResultImpl<>(id, node, (O) null, TaskResultImpl.FAILURE, null);
                        resultQueue.add(result);
                        return result;
                    }
                    if (depResult.isCanceled() || depResult.isFailure()) {
                        var result = new TaskResultImpl<>(id, node, (O) null, TaskResultImpl.CANCELED, null);
                        resultQueue.add(result);
                        return result;
                    }
                    dependencyResults.put(depResult.getId(), depResult);
                }
                var result = getResult(new ExecutionContextImpl<>(id, node, dependencyResults), func);
                resultQueue.add(result);
                return result;
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static <I, N, O> TaskResult<I, N, O> getResult(ExecutionContext<I, N, O> ctx,
            Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func) {
        try {
            return func.apply(ctx);
        } catch (Exception e) {
            return new TaskResultImpl<>(ctx.getId(), ctx.getNode(), null, TaskResultImpl.FAILURE, e);
        }
    }
}
