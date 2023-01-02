package io.quarkus.domino.processor;

public interface TaskResult<I, N, O> {

    boolean isCanceled();

    boolean isFailure();

    boolean isSuccess();

    boolean isSkipped();

    I getId();

    N getNode();

    O getOutcome();

    Exception getException();
}