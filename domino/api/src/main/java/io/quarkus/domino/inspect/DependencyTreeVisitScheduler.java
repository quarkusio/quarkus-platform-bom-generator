package io.quarkus.domino.inspect;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.Collection;

public interface DependencyTreeVisitScheduler {

    static <E> DependencyTreeVisitScheduler sequential(DependencyTreeBuilder treeBuilder,
            DependencyTreeVisitor<E> visitor,
            MessageWriter log,
            int treesTotal) {
        return new SequentialTreeVisitScheduler<>(visitor, log, treesTotal, treeBuilder);
    }

    static <E> DependencyTreeVisitScheduler parallel(DependencyTreeBuilder treeBuilder,
            DependencyTreeVisitor<E> visitor,
            MessageWriter log,
            int treesTotal) {
        return new ParallelTreeVisitScheduler<>(visitor, log, treesTotal, treeBuilder);
    }

    void process(DependencyTreeRequest root);

    void waitForCompletion();

    Collection<DependencyTreeError> getResolutionFailures();

}
