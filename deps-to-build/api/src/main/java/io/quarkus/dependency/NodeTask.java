package io.quarkus.dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class NodeTask<I, N, O> {

    static <I, N, O> NodeTask<I, N, O> of(I id, N node, Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func) {
        return new NodeTask<I, N, O>(id, node, func);
    }

    private final I id;
    private final N node;
    private final Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func;
    private final Map<I, NodeTask<I, N, O>> dependencies = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private CompletableFuture<TaskResult<I, N, O>> cf;

    private NodeTask(I id, N node, Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> func) {
        this.id = id;
        this.node = node;
        this.func = func;
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
                return cf = CompletableFuture.supplyAsync(() -> getResult(new ExecutionContextImpl<>(id, node), func));
            }
            @SuppressWarnings("unchecked")
            final CompletableFuture<TaskResult<I, N, O>>[] deps = (CompletableFuture<TaskResult<I, N, O>>[]) new CompletableFuture<?>[dependencies
                    .size()];
            int ti = 0;
            for (NodeTask<I, N, O> t : dependencies.values()) {
                deps[ti++] = t.schedule();
            }
            return cf = CompletableFuture.allOf(deps).<TaskResult<I, N, O>> thenApplyAsync((v) -> {
                final Map<I, TaskResult<I, N, O>> dependencyResults = new HashMap<>(deps.length);
                for (int i = 0; i < deps.length; ++i) {
                    final TaskResult<I, N, O> result = deps[i].getNow(null);
                    if (result == null) {
                        return new TaskResultImpl<>(id, node, (O) null, TaskResultImpl.FAILURE, null);
                    }
                    if (result.isCanceled() || result.isFailure()) {
                        return new TaskResultImpl<>(id, node, (O) null, TaskResultImpl.CANCELED, null);
                    }
                    dependencyResults.put(result.getId(), result);
                }
                return getResult(new ExecutionContextImpl<>(id, node, dependencyResults), func);
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
