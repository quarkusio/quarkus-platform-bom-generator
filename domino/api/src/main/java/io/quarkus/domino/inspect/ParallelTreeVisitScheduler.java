package io.quarkus.domino.inspect;

import io.quarkus.domino.processor.ExecutionContext;
import io.quarkus.domino.processor.NodeProcessor;
import io.quarkus.domino.processor.ParallelTreeProcessor;
import io.quarkus.domino.processor.TaskResult;
import java.util.List;
import java.util.function.Function;
import org.eclipse.aether.graph.DependencyNode;

class ParallelTreeVisitScheduler<E> extends DependencyTreeVisitSchedulerBase<E> {

    final ParallelTreeProcessor<Integer, DependencyTreeRequest, DependencyNode> treeProcessor;

    ParallelTreeVisitScheduler(DependencyTreeVisitContext<E> ctx, int treesTotal,
            DependencyTreeBuilder treeBuilder, String progressTrackerPrefix) {
        super(ctx, treesTotal, progressTrackerPrefix);
        treeProcessor = ParallelTreeProcessor
                .with(new NodeProcessor<>() {

                    private TaskResult<Integer, DependencyTreeRequest, DependencyNode> apply(
                            ExecutionContext<Integer, DependencyTreeRequest, DependencyNode> execution) {
                        var request = execution.getNode();
                        try {
                            var node = treeBuilder.buildTree(request);
                            ctx.log.info(getResolvedTreeMessage(request.getArtifact()));
                            return execution.success(node);
                        } catch (Exception e) {
                            return execution.failure(e);
                        }
                    }

                    @Override
                    public Integer getNodeId(DependencyTreeRequest request) {
                        return request.getId();
                    }

                    @Override
                    public Iterable<DependencyTreeRequest> getChildren(DependencyTreeRequest node) {
                        return List.of();
                    }

                    @Override
                    public Function<ExecutionContext<Integer, DependencyTreeRequest, DependencyNode>, TaskResult<Integer, DependencyTreeRequest, DependencyNode>> createFunction() {
                        return this::apply;
                    }
                });
    }

    @Override
    public void process(DependencyTreeRequest root) {
        treeProcessor.addRoot(root);
    }

    @Override
    public void waitForCompletion() {
        var results = treeProcessor.schedule().join();
        for (var r : results) {
            if (r.isFailure()) {
                errors.add(new DependencyTreeError(r.getNode(), r.getException()));
                ctx.getLog().error(formatErrorMessage(r.getNode(), r.getException()));
            } else {
                ctx.root = r.getOutcome();
                ctx.visitor.visit(ctx);
            }
        }
    }
}
