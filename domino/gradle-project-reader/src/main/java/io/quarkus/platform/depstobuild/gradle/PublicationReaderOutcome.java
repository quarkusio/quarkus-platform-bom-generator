package io.quarkus.platform.depstobuild.gradle;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.model.GradleModuleVersion;

public class PublicationReaderOutcome implements ResultHandler<List<GradleModuleVersion>> {

    private CompletableFuture<List<GradleModuleVersion>> publications = new CompletableFuture<>();

    public List<GradleModuleVersion> getPublications() {
        try {
            return publications.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain publications", e);
        }
    }

    @Override
    public void onComplete(List<GradleModuleVersion> result) {
        publications.complete(result);
    }

    @Override
    public void onFailure(GradleConnectionException failure) {
        failure.printStackTrace();
    }
}
