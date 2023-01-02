package io.quarkus.domino.processor;

import java.util.Map;

class ExecutionContextImpl<I, N, O> implements ExecutionContext<I, N, O> {

    private final I id;
    private final N node;
    private final Map<I, TaskResult<I, N, O>> dependencyResults;

    ExecutionContextImpl(I id, N node) {
        this(id, node, Map.of());
    }

    ExecutionContextImpl(I id, N node, Map<I, TaskResult<I, N, O>> dependencyResults) {
        this.id = id;
        this.node = node;
        this.dependencyResults = dependencyResults;
    }

    @Override
    public I getId() {
        return id;
    }

    @Override
    public N getNode() {
        return node;
    }

    @Override
    public TaskResult<I, N, O> canceled(O o) {
        return new TaskResultImpl<>(id, node, o, TaskResultImpl.CANCELED, null);
    }

    @Override
    public TaskResult<I, N, O> failure(O o, Exception e) {
        return new TaskResultImpl<>(id, node, o, TaskResultImpl.FAILURE, e);
    }

    @Override
    public TaskResult<I, N, O> success(O o) {
        return new TaskResultImpl<>(id, node, o, TaskResultImpl.SUCCESS, null);
    }

    @Override
    public TaskResult<I, N, O> skipped(O o) {
        return new TaskResultImpl<>(id, node, o, TaskResultImpl.SKIPPED, null);
    }

    @Override
    public Iterable<I> getDependencies() {
        return dependencyResults.keySet();
    }

    @Override
    public TaskResult<I, N, O> getDependencyResult(I id) {
        return dependencyResults.get(id);
    }
}