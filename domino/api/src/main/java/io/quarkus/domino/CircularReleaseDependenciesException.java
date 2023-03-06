package io.quarkus.domino;

import java.util.Collection;

public class CircularReleaseDependenciesException extends RuntimeException {

    private static final long serialVersionUID = 1133156259737572937L;

    private final Collection<CircularReleaseDependency> circularReleaseDeps;

    public CircularReleaseDependenciesException(Collection<CircularReleaseDependency> circularReleaseDeps) {
        super("The following circular release dependencies were detected: " + circularReleaseDeps
                + ". Make sure the SCM URL and tags were properly identified.");
        if (circularReleaseDeps == null || circularReleaseDeps.isEmpty()) {
            throw new IllegalArgumentException("No circular release dependencies were provided");
        }
        this.circularReleaseDeps = circularReleaseDeps;
    }

    public Collection<CircularReleaseDependency> getCircularReleaseDependencies() {
        return circularReleaseDeps;
    }
}
