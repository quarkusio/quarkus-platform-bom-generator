package io.quarkus.domino.processor;

import java.util.function.Function;

public interface NodeProcessor<I, N, O> {

    I getNodeId(N node);

    Iterable<N> getChildren(N node);

    Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> createFunction();
}
