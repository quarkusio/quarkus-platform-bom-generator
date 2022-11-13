package io.quarkus.dependency;

class TaskResultImpl<I, N, O> implements TaskResult<I, N, O> {

    /* @formatter:off */
	static final int CANCELED = 0x00001;
	static final int FAILURE  = 0x00010;
	static final int SUCCESS  = 0x00100;
	static final int SKIPPED  = 0x01000;
	/* @formatter:on */

    private final I id;
    private final N node;
    private final O o;
    private final int status;
    private final Exception e;

    public TaskResultImpl(I id, N node, O o, int status, Exception e) {
        this.id = id;
        this.node = node;
        this.o = o;
        this.status = status;
        this.e = e;
    }

    private boolean isFlagSet(int flag) {
        return (status & flag) > 0;
    }

    @Override
    public boolean isCanceled() {
        return isFlagSet(CANCELED);
    }

    @Override
    public boolean isFailure() {
        return isFlagSet(FAILURE);
    }

    @Override
    public boolean isSuccess() {
        return isFlagSet(SUCCESS);
    }

    @Override
    public boolean isSkipped() {
        return isFlagSet(SKIPPED);
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
    public O getOutcome() {
        return o;
    }

    @Override
    public Exception getException() {
        return e;
    }
}