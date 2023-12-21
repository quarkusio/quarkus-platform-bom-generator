package io.quarkus.domino.inspect;

import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.processor.ExecutionContext;
import io.quarkus.domino.processor.NodeProcessor;
import io.quarkus.domino.processor.ParallelTreeProcessor;
import io.quarkus.domino.processor.TaskResult;
import java.util.List;
import java.util.function.Function;
import org.eclipse.aether.graph.DependencyNode;

public class ParallelTreeVisitScheduler<E> extends DependencyTreeVisitSchedulerBase<E> {

    final ParallelTreeProcessor<String, DependencyTreeRequest, DependencyNode> treeProcessor;
    private final DependencyTreeVisitor<E> visitor;
    private final MessageWriter log;

    public ParallelTreeVisitScheduler(DependencyTreeVisitor<E> visitor, MessageWriter log, int treesTotal,
            DependencyTreeBuilder treeBuilder) {
        super(visitor, log, treesTotal);
        this.visitor = visitor;
        this.log = log;
        treeProcessor = ParallelTreeProcessor
                .with(new NodeProcessor<>() {

                    private TaskResult<String, DependencyTreeRequest, DependencyNode> apply(
                            ExecutionContext<String, DependencyTreeRequest, DependencyNode> execution) {
                        var request = execution.getNode();
                        try {
                            var node = treeBuilder.buildTree(request);
                            log.info(getResolvedTreeMessage(request.getArtifact()));
                            return execution.success(node);
                        } catch (Exception e) {
                            return execution.failure(e);
                        }
                    }

                    @Override
                    public String getNodeId(DependencyTreeRequest request) {
                        return request.getId();
                    }

                    @Override
                    public Iterable<DependencyTreeRequest> getChildren(DependencyTreeRequest node) {
                        return List.of();
                    }

                    @Override
                    public Function<ExecutionContext<String, DependencyTreeRequest, DependencyNode>, TaskResult<String, DependencyTreeRequest, DependencyNode>> createFunction() {
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
                log.error(formatErrorMessage(r.getNode(), r.getException()));
            } else {
                ctx.root = r.getOutcome();
                visitor.visit(ctx);
            }
        }
    }
}
