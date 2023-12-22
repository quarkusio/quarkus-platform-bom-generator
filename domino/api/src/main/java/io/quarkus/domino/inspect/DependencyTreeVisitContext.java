package io.quarkus.domino.inspect;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.Objects;
import org.eclipse.aether.graph.DependencyNode;

class DependencyTreeVisitContext<E> implements DependencyTreeVisitor.DependencyTreeVisit<E> {

    final DependencyTreeVisitor<E> visitor;
    final MessageWriter log;
    DependencyNode root;

    DependencyTreeVisitContext(DependencyTreeVisitor<E> visitor, MessageWriter log) {
        this.visitor = visitor;
        this.log = log;
    }

    @Override
    public DependencyNode getRoot() {
        return root;
    }

    @Override
    public MessageWriter getLog() {
        return log;
    }

    @Override
    public void pushEvent(E event) {
        Objects.requireNonNull(root, "Dependency tree root node is null");
        visitor.onEvent(event, log);
    }
}
