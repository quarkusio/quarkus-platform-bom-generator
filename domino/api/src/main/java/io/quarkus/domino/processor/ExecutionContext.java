package io.quarkus.domino.processor;

public interface ExecutionContext<I, N, O> {

    I getId();

    N getNode();

    default TaskResult<I, N, O> canceled() {
        return canceled(null);
    }

    TaskResult<I, N, O> canceled(O o);

    default TaskResult<I, N, O> failure() {
        return failure(null, null);
    }

    default TaskResult<I, N, O> failure(Exception e) {
        return failure(null, e);
    }

    default TaskResult<I, N, O> failure(O o) {
        return failure(o, null);
    }

    TaskResult<I, N, O> failure(O o, Exception e);

    default TaskResult<I, N, O> success() {
        return success(null);
    }

    TaskResult<I, N, O> success(O o);

    default TaskResult<I, N, O> skipped() {
        return skipped(null);
    }

    TaskResult<I, N, O> skipped(O o);

    Iterable<I> getDependencies();

    TaskResult<I, N, O> getDependencyResult(I id);
}