package io.quarkus.domino.gradle;

import java.util.concurrent.CompletableFuture;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;

public class GradleActionOutcome<T> implements ResultHandler<T> {

    public static <T> GradleActionOutcome<T> of() {
        return new GradleActionOutcome<T>();
    }

    private CompletableFuture<T> future = new CompletableFuture<>();

    public T getResult() {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Gradle action", e);
        }
    }

    @Override
    public void onComplete(T result) {
        future.complete(result);
    }

    @Override
    public void onFailure(GradleConnectionException failure) {
        failure.printStackTrace();
        future.complete(null);
    }
}
