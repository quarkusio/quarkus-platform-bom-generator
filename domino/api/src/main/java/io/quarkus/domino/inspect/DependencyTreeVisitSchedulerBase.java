package io.quarkus.domino.inspect;

import java.util.Deque;
import java.util.Formatter;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.aether.artifact.Artifact;

abstract class DependencyTreeVisitSchedulerBase<E> implements DependencyTreeVisitScheduler {

    private static final String FORMAT_BASE = "[%s/%s %.1f%%] ";

    protected final Deque<DependencyTreeError> errors = new ConcurrentLinkedDeque<>();
    protected final DependencyTreeVisitContext<E> ctx;
    protected final AtomicInteger counter = new AtomicInteger();
    protected final int rootsTotal;
    private final String progressTrackerPrefix;

    DependencyTreeVisitSchedulerBase(DependencyTreeVisitContext<E> ctx, int rootsTotal, String progressTrackerPrefix) {
        this.ctx = ctx;
        this.rootsTotal = rootsTotal;
        this.progressTrackerPrefix = progressTrackerPrefix;
    }

    @Override
    public Deque<DependencyTreeError> getResolutionFailures() {
        return errors;
    }

    protected String getResolvedTreeMessage(Artifact a) {
        var sb = new StringBuilder(180);
        var formatter = new Formatter(sb);
        var treeIndex = counter.incrementAndGet();
        final double percents = ((double) treeIndex * 100) / rootsTotal;

        formatter.format(FORMAT_BASE, treeIndex, rootsTotal, percents);
        if (progressTrackerPrefix != null) {
            sb.append(progressTrackerPrefix);
        }

        sb.append(a.getGroupId()).append(':').append(a.getArtifactId()).append(':');
        if (!a.getClassifier().isEmpty()) {
            sb.append(a.getClassifier()).append(':');
        }
        if (!"jar".equals(a.getExtension())) {
            if (a.getClassifier().isEmpty()) {
                sb.append(':');
            }
            sb.append(a.getExtension()).append(':');
        }
        return sb.append(a.getVersion()).toString();
    }

    protected String formatErrorMessage(DependencyTreeRequest request, Throwable e) {
        var sb = new StringBuilder();
        sb.append("Failed to process dependencies of ").append(request.getArtifact());
        if (e != null) {
            if (e.getCause() != null) {
                // the outer one is most probably a tree processing wrapper of the actual one thrown by the resolver
                e = e.getCause();
            }
            var error = e.getLocalizedMessage();
            sb.append(" because ").append(Character.toLowerCase(error.charAt(0))).append(error.substring(1));
        }
        return sb.toString();
    }
}
