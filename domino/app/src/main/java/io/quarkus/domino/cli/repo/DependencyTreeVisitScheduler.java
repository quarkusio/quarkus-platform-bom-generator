package io.quarkus.domino.cli.repo;

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
            public void scheduleProcessing(Artifact rootArtifact, List<Dependency> constraints,
                    Collection<Exclusion> exclusions) {
                final DependencyNode root;
                try {
                    root = treeBuilder.buildTree(rootArtifact, constraints, exclusions);
                    log.info(getResolvedTreeMessage(rootArtifact));
                } catch (Exception e) {
                    resolutionFailures.add(rootArtifact);
                    log.error(e.getLocalizedMessage());
                    return;
                }
                ctx.root = root;
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

        return new BaseDependencyTreeProcessScheduler<E>(processor, log, treesTotal) {

            final ParallelTreeProcessor<String, DependencyTreeRequest, DependencyNode> treeProcessor = ParallelTreeProcessor
                    .with(new NodeProcessor<>() {

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
                            return ctx -> {
                                var request = ctx.getNode();
                                try {
                                    var node = treeBuilder.buildTree(request.root, request.constraints, request.exclusions);
                                    log.info(getResolvedTreeMessage(request.root));
                                    return ctx.success(node);
                                } catch (Exception e) {
                                    return ctx.failure(e);
                                }
                            };
                        }
                    });

            int scheduledTotal = 0;

            @Override
            public void scheduleProcessing(Artifact rootArtifact, List<Dependency> constraints,
                    Collection<Exclusion> exclusions) {
                ++scheduledTotal;
                treeProcessor.addRoot(new DependencyTreeRequest(rootArtifact, constraints, exclusions));
            }

            @Override
            public void waitForCompletion() {
                var results = treeProcessor.schedule().join();
                for (var r : results) {
                    if (r.isFailure()) {
                        resolutionFailures.add(r.getNode().root);
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

    void scheduleProcessing(Artifact rootArtifact, List<Dependency> constraints, Collection<Exclusion> exclusions);

    void waitForCompletion();

    Collection<Artifact> getResolutionFailures();

    interface TreeProcessingResultHandler<R> {

        void handleResult(R result, MessageWriter log);
    }

    public static class DependencyTreeRequest {

        private final Artifact root;
        private final List<Dependency> constraints;
        private final Collection<Exclusion> exclusions;

        public DependencyTreeRequest(Artifact root, List<Dependency> constraints, Collection<Exclusion> exclusions) {
            this.root = root;
            this.constraints = constraints;
            this.exclusions = exclusions;
        }

        public String getId() {
            return root.toString();
        }
    }
}
