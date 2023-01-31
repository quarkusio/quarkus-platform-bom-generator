package io.quarkus.domino.gradle;

import java.util.concurrent.CompletableFuture;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;

public class GradleActionOutcome<T> implements ResultHandler<T> {

    public static <T> GradleActionOutcome<T> of() {
        return new GradleActionOutcome<T>();
    }

    private CompletableFuture<T> future = new CompletableFuture<>();
    private Exception error;

    public T getResult() {
        try {
            T result = future.get();
            if (error == null) {
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to perform a Gradle action", e);
        }
        throw new RuntimeException("Failed to perform a Gradle action", error);
    }

    @Override
    public void onComplete(T result) {
        future.complete(result);
    }

    @Override
    public void onFailure(GradleConnectionException failure) {
        this.error = failure;
        future.complete(null);
    }
}
