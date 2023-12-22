package io.quarkus.domino.inspect;

import org.eclipse.aether.graph.DependencyNode;

class SequentialTreeVisitScheduler<E> extends DependencyTreeVisitSchedulerBase<E> {

    private final DependencyTreeBuilder treeBuilder;

    SequentialTreeVisitScheduler(DependencyTreeVisitContext<E> ctx, int treesTotal,
            DependencyTreeBuilder treeBuilder, String progressTrackerPrefix) {
        super(ctx, treesTotal, progressTrackerPrefix);
        this.treeBuilder = treeBuilder;
    }

    @Override
    public void process(DependencyTreeRequest req) {
        final DependencyNode rootNode;
        try {
            rootNode = treeBuilder.buildTree(req);
            ctx.log.info(getResolvedTreeMessage(rootNode.getArtifact()));
        } catch (Exception e) {
            errors.add(new DependencyTreeError(req, e));
            ctx.log.error(formatErrorMessage(req, e));
            return;
        }
        ctx.root = rootNode;
        ctx.visitor.visit(ctx);
    }

    @Override
    public void waitForCompletion() {
    }
}
