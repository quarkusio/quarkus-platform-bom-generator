package io.quarkus.domino.inspect;

public class DependencyTreeError {

    private final DependencyTreeRequest request;
    private final Throwable error;

    public DependencyTreeError(DependencyTreeRequest request, Throwable error) {
        this.request = request;
        this.error = error;
    }

    public DependencyTreeRequest getRequest() {
        return request;
    }

    public Throwable getError() {
        return error;
    }
}
