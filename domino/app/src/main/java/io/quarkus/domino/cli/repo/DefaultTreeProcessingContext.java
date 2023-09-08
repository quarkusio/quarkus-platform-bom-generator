package io.quarkus.domino.cli.repo;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.Objects;
import org.eclipse.aether.graph.DependencyNode;

class DefaultTreeProcessingContext<E> implements DependencyTreeVisitor.DependencyTreeVisit<E> {

    private final DependencyTreeVisitor<E> processor;
    private final MessageWriter log;
    DependencyNode root;

    DefaultTreeProcessingContext(DependencyTreeVisitor<E> processor, MessageWriter log) {
        this.processor = processor;
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
        processor.onEvent(event, log);
    }
}
