package io.quarkus.domino.inspect;

import java.util.Collection;

public interface DependencyTreeVisitScheduler {

    void process(DependencyTreeRequest root);

    void waitForCompletion();

    Collection<DependencyTreeError> getResolutionFailures();

}
