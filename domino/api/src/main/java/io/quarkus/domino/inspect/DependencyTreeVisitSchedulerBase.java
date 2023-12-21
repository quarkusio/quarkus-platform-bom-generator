package io.quarkus.domino.inspect;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.aether.artifact.Artifact;

abstract class DependencyTreeVisitSchedulerBase<E> implements DependencyTreeVisitScheduler {

    private static final String FORMAT_BASE = "[%s/%s %.1f%%] ";

    protected final List<DependencyTreeError> errors = new ArrayList<>();
    protected final DependencyTreeVisitContext<E> ctx;
    protected final AtomicInteger counter = new AtomicInteger();
    protected final int rootsTotal;

    DependencyTreeVisitSchedulerBase(DependencyTreeVisitor<E> visitor, MessageWriter log, int rootsTotal) {
        ctx = new DependencyTreeVisitContext<>(visitor, log);
        this.rootsTotal = rootsTotal;
    }

    @Override
    public List<DependencyTreeError> getResolutionFailures() {
        return errors;
    }

    protected String getResolvedTreeMessage(Artifact a) {
        var sb = new StringBuilder(160);
        var formatter = new Formatter(sb);
        var treeIndex = counter.incrementAndGet();
        final double percents = ((double) treeIndex * 100) / rootsTotal;

        formatter.format(FORMAT_BASE, treeIndex, rootsTotal, percents);

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

    protected String formatErrorMessage(DependencyTreeRequest request, Exception e) {
        var sb = new StringBuilder();
        sb.append("Failed to process dependencies of ").append(request.getArtifact());
        if (e != null) {
            var error = e.getLocalizedMessage();
            sb.append(" because ").append(Character.toLowerCase(error.charAt(0))).append(error.substring(1));
        }
        return sb.toString();
    }
}
