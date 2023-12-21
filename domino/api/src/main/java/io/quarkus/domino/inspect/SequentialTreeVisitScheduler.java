package io.quarkus.domino.inspect;

import io.quarkus.devtools.messagewriter.MessageWriter;
import org.eclipse.aether.graph.DependencyNode;

public class SequentialTreeVisitScheduler<E> extends DependencyTreeVisitSchedulerBase<E> {

    private final DependencyTreeVisitor<E> visitor;
    private final MessageWriter log;
    private final DependencyTreeBuilder treeBuilder;

    public SequentialTreeVisitScheduler(DependencyTreeVisitor<E> visitor, MessageWriter log, int treesTotal,
            DependencyTreeBuilder treeBuilder) {
        super(visitor, log, treesTotal);
        this.visitor = visitor;
        this.log = log;
        this.treeBuilder = treeBuilder;
    }

    @Override
    public void process(DependencyTreeRequest req) {
        final DependencyNode rootNode;
        try {
            rootNode = treeBuilder.buildTree(req);
            log.info(getResolvedTreeMessage(rootNode.getArtifact()));
        } catch (Exception e) {
            errors.add(new DependencyTreeError(req, e));
            log.error(formatErrorMessage(req, e));
            return;
        }
        ctx.root = rootNode;
        visitor.visit(ctx);
    }

    @Override
    public void waitForCompletion() {
    }
}
