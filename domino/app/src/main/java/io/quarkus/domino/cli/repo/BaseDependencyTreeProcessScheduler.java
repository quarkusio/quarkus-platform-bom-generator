package io.quarkus.domino.cli.repo;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.aether.artifact.Artifact;

abstract class BaseDependencyTreeProcessScheduler<E> implements DependencyTreeVisitScheduler {

    private static final long ESTIMATE_TIME_AFTER_MS = 60 * 1000;
    private static final int ESTIMATE_TIME_AFTER_PERCENTS = 42;

    private static final String FORMAT_BASE = "[%s/%s %.1f%%] ";
    private static final String FORMAT_SECONDS_LEFT = "[%s/%s %.1f%%, ~ %ds left] ";
    private static final String FORMAT_MINUTES_LEFT = "[%s/%s %.1f%%, ~ %dm %ds left] ";
    private static final String FORMAT_HOURS_LEFT = "[%s/%s %.1f%%, ~ %dh %dm %dsec left] ";

    protected final List<Artifact> resolutionFailures = new ArrayList<>();
    protected final DefaultTreeProcessingContext<E> ctx;
    protected final AtomicInteger counter = new AtomicInteger();
    protected final int rootsTotal;
    private long startTime = -1;
    private long totalSecEstimate = -1;
    private long firstEstimateStart;

    private final long[] treeTimes;
    private final AtomicInteger treeIndex = new AtomicInteger();

    BaseDependencyTreeProcessScheduler(DependencyTreeVisitor<E> processor, MessageWriter log, int rootsTotal) {
        ctx = new DefaultTreeProcessingContext<E>(processor, log);
        this.rootsTotal = rootsTotal;
        treeTimes = new long[rootsTotal];
    }

    @Override
    public List<Artifact> getResolutionFailures() {
        return resolutionFailures;
    }

    protected String getResolvedTreeMessage(Artifact a) {
        var sb = new StringBuilder(160);
        var formatter = new Formatter(sb);
        var treeIndex = counter.incrementAndGet();
        final double percents = ((double) treeIndex * 100) / rootsTotal;

        boolean estimateTime = false;// percents >= ESTIMATE_TIME_AFTER_PERCENTS && treeIndex > 1;
        if (startTime == -1) {
            startTime = System.currentTimeMillis();
            estimateTime = false;
        }

        if (estimateTime && totalSecEstimate == -1) {
            firstEstimateStart = System.currentTimeMillis();
            totalSecEstimate = ((rootsTotal - treeIndex) * (firstEstimateStart - startTime) / (treeIndex - 1)) / 1000;
        }

        if (estimateTime) {
            final long durationMs = System.currentTimeMillis() - startTime;
            if (durationMs > ESTIMATE_TIME_AFTER_MS) {
                final long remainingSeconds = Math.round((100 - percents) * totalSecEstimate / 100);
                //final long remainingSeconds = ((rootsTotal - treeIndex) * durationMs / (treeIndex - 1)) / 1000;
                final long hours = remainingSeconds / 3600;
                final long minutes = (remainingSeconds % 3600) / 60;
                final long seconds = remainingSeconds % 60;
                if (hours > 0) {
                    formatter.format(FORMAT_HOURS_LEFT,
                            treeIndex,
                            rootsTotal,
                            percents,
                            hours,
                            minutes,
                            seconds);
                } else if (minutes > 0) {
                    formatter.format(FORMAT_MINUTES_LEFT,
                            treeIndex,
                            rootsTotal,
                            percents,
                            minutes,
                            seconds);
                } else if (seconds > 0) {
                    formatter.format(FORMAT_SECONDS_LEFT,
                            treeIndex,
                            rootsTotal,
                            percents,
                            seconds);
                } else {
                    formatter.format(FORMAT_BASE,
                            treeIndex,
                            rootsTotal,
                            percents);
                }
            } else {
                formatter.format(FORMAT_BASE, treeIndex, rootsTotal, percents);
            }
        } else {
            formatter.format(FORMAT_BASE, treeIndex, rootsTotal, percents);
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
        /* @formatter:off
        if (rootsTotal - 1 == treeIndex) {
            long hours = totalSecEstimate / 3600;
            long minutes = (totalSecEstimate % 3600) / 60;
            long seconds = totalSecEstimate % 60;

            System.out.println("Total estimate was " + hours + ":" + minutes + ":" + seconds);

            var est = (System.currentTimeMillis() - startTime) / 1000;
            hours = est / 3600;
            minutes = (est % 3600) / 60;
            seconds = est % 60;
            System.out.println("Actual total is " + hours + ":" + minutes + ":" + seconds);

            est = (firstEstimateStart - startTime) / 1000;
            hours = est / 3600;
            minutes = (est % 3600) / 60;
            seconds = est % 60;
            System.out.println("Before first estimate " + hours + ":" + minutes + ":" + seconds);

            est = (System.currentTimeMillis() - firstEstimateStart) / 1000;
            hours = est / 3600;
            minutes = (est % 3600) / 60;
            seconds = est % 60;
            System.out.println("Since first estimate " + hours + ":" + minutes + ":" + seconds);
        }
 @formatter:on */
        return sb.append(a.getVersion()).toString();
    }

    public static void main(String[] args) throws Exception {

        double[] x1 = { 2, 3, 4, 5, 6, 7, 8, 9 };
        double[] y1 = { 4, 8, 16, 32, 64, 128, 256, 512 };
        double[] x2 = { 10 };

        for (int i = 0; i < x2.length; i++) {
            System.out.println("Value: " + x2[i] + " => extrapolation: " + extraPolate(x1, y1, x2[i]));
        }

        var e = new Extrapolator();
        e.pushSample(2);
        e.pushSample(4);
        e.pushSample(8);
        e.pushSample(16);
        e.pushSample(32);
        e.pushSample(64);
        e.pushSample(128);
        e.pushSample(256);
        e.pushSample(512);

        System.out.println(e.extrapolate());
    }

    static double extraPolate(double[] x, double[] y, double x2) {
        var lowIndex = 2;
        return y[y.length - 1] + (x2 - x[x.length - lowIndex]) / (x[x.length - 1] - x[x.length - lowIndex])
                * (y[y.length - 1] - y[y.length - lowIndex]);
    }

    private static class Extrapolator {

        private static final int MAX_SAMPLES = 6;
        private static final int LOW_INDEX = 1;
        private final AtomicLong[] samples = new AtomicLong[MAX_SAMPLES];
        private final AtomicInteger sampleCounter = new AtomicInteger(-1);

        public Extrapolator() {
            for (int i = 0; i < MAX_SAMPLES; ++i) {
                samples[i] = new AtomicLong();
            }
        }

        public void pushSample(long sample) {
            var sampleIndex = sampleCounter.incrementAndGet();
            samples[sampleIndex % MAX_SAMPLES].set(sample);
        }

        public long extrapolate() {
            var sampleCounter = this.sampleCounter.get();
            var sampleIndex = sampleCounter % MAX_SAMPLES;
            var lastSample = samples[sampleIndex].get();
            var lowSample = samples[(MAX_SAMPLES + sampleIndex - LOW_INDEX) % MAX_SAMPLES].get();
            return lastSample
                    + (sampleCounter + 1 - (sampleCounter - LOW_INDEX)) / (sampleCounter - (sampleCounter - LOW_INDEX))
                            * (lastSample - lowSample);
        }
    }
}
