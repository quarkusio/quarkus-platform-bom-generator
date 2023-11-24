package io.quarkus.domino.tree;

import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.processor.ExecutionContext;
import io.quarkus.domino.processor.NodeProcessor;
import io.quarkus.domino.processor.ParallelTreeProcessor;
import io.quarkus.domino.processor.TaskResult;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;

public interface DependencyTreeVisitScheduler {

    static <E> DependencyTreeVisitScheduler sequencial(DependencyTreeBuilder treeBuilder,
            DependencyTreeVisitor<E> processor,
            MessageWriter log,
            int treesTotal) {
        return new BaseDependencyTreeProcessScheduler<>(processor, log, treesTotal) {

            @Override
            public void scheduleProcessing(DependencyTreeRoot root) {
                final DependencyNode rootNode;
                try {
                    rootNode = treeBuilder.buildTree(root);
                    log.info(getResolvedTreeMessage(rootNode.getArtifact()));
                } catch (Exception e) {
                    resolutionFailures.add(root.getArtifact());
                    log.error(e.getLocalizedMessage());
                    return;
                }
                ctx.root = rootNode;
                processor.visitTree(ctx);
            }

            @Override
            public void waitForCompletion() {
            }
        };
    }

    static <E> DependencyTreeVisitScheduler parallel(DependencyTreeBuilder treeBuilder,
            DependencyTreeVisitor<E> processor,
            MessageWriter log,
            int treesTotal) {

        return new BaseDependencyTreeProcessScheduler<>(processor, log, treesTotal) {

            final ParallelTreeProcessor<String, DependencyTreeRoot, DependencyNode> treeProcessor = ParallelTreeProcessor
                    .with(new NodeProcessor<>() {

                        @Override
                        public String getNodeId(DependencyTreeRoot request) {
                            return request.getId();
                        }

                        @Override
                        public Iterable<DependencyTreeRoot> getChildren(DependencyTreeRoot node) {
                            return List.of();
                        }

                        @Override
                        public Function<ExecutionContext<String, DependencyTreeRoot, DependencyNode>, TaskResult<String, DependencyTreeRoot, DependencyNode>> createFunction() {
                            return ctx -> {
                                var request = ctx.getNode();
                                try {
                                    var node = treeBuilder.buildTree(request);
                                    log.info(getResolvedTreeMessage(request.getArtifact()));
                                    return ctx.success(node);
                                } catch (Exception e) {
                                    return ctx.failure(e);
                                }
                            };
                        }
                    });

            int scheduledTotal = 0;

            @Override
            public void scheduleProcessing(DependencyTreeRoot root) {
                ++scheduledTotal;
                treeProcessor.addRoot(root);
            }

            @Override
            public void waitForCompletion() {
                var results = treeProcessor.schedule().join();
                for (var r : results) {
                    if (r.isFailure()) {
                        resolutionFailures.add(r.getNode().getArtifact());
                        log.error("Failed to resolve dependencies of " + r.getId());
                        //if (r.getException() != null) {
                        //    r.getException().printStackTrace();
                        //}
                    } else {
                        ctx.root = r.getOutcome();
                        processor.visitTree(ctx);
                    }
                }
            }
        };
    }

    default void scheduleProcessing(Artifact rootArtifact, List<Dependency> constraints, Collection<Exclusion> exclusions) {
        scheduleProcessing(new DependencyTreeRoot(rootArtifact, constraints, exclusions));
    }

    void scheduleProcessing(DependencyTreeRoot root);

    void waitForCompletion();

    Collection<Artifact> getResolutionFailures();

    interface TreeProcessingResultHandler<R> {

        void handleResult(R result, MessageWriter log);
    }

}
