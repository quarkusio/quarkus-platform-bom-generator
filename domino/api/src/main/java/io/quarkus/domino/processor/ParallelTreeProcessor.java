package io.quarkus.domino.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class ParallelTreeProcessor<I, N, O> {

    public static <I, N, O> ParallelTreeProcessor<I, N, O> with(NodeProcessor<I, N, O> nodeProcessor) {
        return new ParallelTreeProcessor<>(nodeProcessor);
    }

    private final NodeProcessor<I, N, O> nodeProcessor;
    private final Function<ExecutionContext<I, N, O>, TaskResult<I, N, O>> nodeFunc;
    private final List<NodeTask<I, N, O>> rootTasks = new ArrayList<>();
    private final Map<I, NodeTask<I, N, O>> allTasks = new ConcurrentHashMap<>();
    private final Queue<TaskResult<I, N, O>> resultQueue = new ConcurrentLinkedQueue<>();

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
            return new ArrayList<>(resultQueue);
            //return List.of(resultQueue);
            /*
             * final List<TaskResult<I, N, O>> results = new ArrayList<>(allTasks.size());
             * for (CompletableFuture<TaskResult<I, N, O>> rootResult : rootResults) {
             * results.add(rootResult.getNow(null));
             * }
             * return results;
             */
        });
    }

    public void addRoot(N root) {
        rootTasks.add(visit(root, new LinkedHashMap<>()));
    }

    private NodeTask<I, N, O> visit(N node, Map<I, N> visited) {
        var nodeId = nodeProcessor.getNodeId(node);
        {
            var prevNode = visited.put(nodeId, node);
            if (prevNode != null) {
                var sb = new StringBuilder("Circular dependency detected: ");
                var i = visited.entrySet().iterator();
                while (i.hasNext()) {
                    var e = i.next();
                    if (e.getKey().equals(nodeId)) {
                        sb.append(e.getValue());
                        break;
                    }
                }
                while (i.hasNext()) {
                    var e = i.next();
                    sb.append(" -> ").append(e.getValue());
                }
                sb.append(" -> ").append(node);
                throw new IllegalArgumentException(sb.toString());
            }
        }

        final NodeTask<I, N, O> nodeTask = allTasks.computeIfAbsent(nodeId,
                id -> NodeTask.of(id, node, nodeFunc, resultQueue));
        for (N c : nodeProcessor.getChildren(node)) {
            nodeTask.dependsOn(visit(c, visited));
        }
        visited.remove(nodeId);
        return nodeTask;
    }
}
