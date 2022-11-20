package io.quarkus.dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ParallelTreeProcessor<I, N, O> {

    public static <I, N, O> ParallelTreeProcessor<I, N, O> with(NodeProcessor<I, N, O> nodeProcessor) {
        return new ParallelTreeProcessor<>(nodeProcessor);
    }

    private final NodeProcessor<I, N, O> nodeProcessor;
    private final Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> nodeFunc;
    private final List<NodeTask<I, N, O>> rootTasks = new ArrayList<>();
    private final Map<I, NodeTask<I, N, O>> allTasks = new ConcurrentHashMap<>();

    private ParallelTreeProcessor(NodeProcessor<I, N, O> nodeProcessor) {
        this.nodeProcessor = nodeProcessor;
        this.nodeFunc = nodeProcessor.createFunction();
    }

    public CompletableFuture<List<TaskResult<I, N, O>>> schedule() {
        final List<CompletableFuture<TaskResult<I, N, O>>> rootResults = new ArrayList<>();
        for (NodeTask<I, N, O> t : rootTasks) {
            rootResults.add(t.schedule());
        }
        return CompletableFuture.allOf(rootResults.toArray(new CompletableFuture<?>[0])).thenApplyAsync((n) -> {
            final List<TaskResult<I, N, O>> results = new ArrayList<>(rootTasks.size());
            for (CompletableFuture<TaskResult<I, N, O>> rootResult : rootResults) {
                results.add(rootResult.getNow(null));
            }
            return results;
        });
    }

    public void addRoot(N root) {
        rootTasks.add(visit(root));
    }

    private NodeTask<I, N, O> visit(N node) {
        final NodeTask<I, N, O> nodeTask = allTasks.computeIfAbsent(nodeProcessor.getNodeId(node),
                id -> NodeTask.of(id, node, nodeFunc));
        for (N c : nodeProcessor.getChildren(node)) {
            nodeTask.dependsOn(visit(c));
        }
        return nodeTask;
    }
}
