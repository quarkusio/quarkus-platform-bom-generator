package io.quarkus.domino.inspect;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.Collection;
import org.eclipse.aether.graph.DependencyNode;

public interface DependencyTreeVisitor<E> {

    interface DependencyTreeVisit<E> {

        DependencyNode getRoot();

        MessageWriter getLog();

        void pushEvent(E event);
    }

    void visit(DependencyTreeVisit<E> ctx);

    void onEvent(E event, MessageWriter log);

    void handleResolutionFailures(Collection<DependencyTreeError> errors);
}
