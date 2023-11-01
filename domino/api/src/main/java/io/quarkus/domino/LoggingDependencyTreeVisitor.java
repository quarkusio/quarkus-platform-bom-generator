package io.quarkus.domino;

import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.dependency.ArtifactCoords;

public class LoggingDependencyTreeVisitor implements DependencyTreeVisitor {

    private static final String NOT_MANAGED = " [not managed]";

    private final MessageWriter log;
    private final boolean asComments;
    private int level;
    private boolean loggingEnabled;
    private ArtifactSet logTreesFor;

    public LoggingDependencyTreeVisitor(MessageWriter log, boolean asComments, String logTreesFor) {
        this.log = log;
        this.asComments = asComments;
        if (logTreesFor != null) {
            final ArtifactSet.Builder builder = ArtifactSet.builder();
            final String[] arr = logTreesFor.split(",");
            for (String s : arr) {
                builder.include(s);
            }
            this.logTreesFor = builder.build();
        }
    }

    @Override
    public void beforeAllRoots() {
    }

    @Override
    public void afterAllRoots() {
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        final ArtifactCoords coords = visit.getCoords();
        loggingEnabled = logTreesFor == null || logTreesFor.contains(coords);
        if (!loggingEnabled) {
            return;
        }
        if (visit.isManaged()) {
            logComment(coords.toCompactCoords());
        } else {
            logComment(coords.toCompactCoords() + NOT_MANAGED);
        }
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        if (loggingEnabled) {
            logComment("");
        }
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
        if (!loggingEnabled) {
            return;
        }
        ++level;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; ++i) {
            sb.append("  ");
        }
        sb.append(visit.getCoords().toCompactCoords());
        if (!visit.isManaged()) {
            sb.append(' ').append(NOT_MANAGED);
        }
        logComment(sb.toString());
    }

    @Override
    public void leaveDependency(DependencyVisit visit) {
        if (!loggingEnabled) {
            return;
        }
        --level;
    }

    @Override
    public void enterParentPom(DependencyVisit visit) {
        if (!loggingEnabled) {
            return;
        }
        ++level;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; ++i) {
            sb.append("  ");
        }
        sb.append(visit.getCoords().toCompactCoords()).append(" [parent pom]");
        logComment(sb.toString());
    }

    @Override
    public void leaveParentPom(DependencyVisit visit) {
        if (!loggingEnabled) {
            return;
        }
        --level;
    }

    @Override
    public void enterBomImport(DependencyVisit visit) {
        if (!loggingEnabled) {
            return;
        }
        ++level;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; ++i) {
            sb.append("  ");
        }
        sb.append(visit.getCoords().toCompactCoords()).append(" [bom import]");
        logComment(sb.toString());
    }

    @Override
    public void leaveBomImport(DependencyVisit visit) {
        if (!loggingEnabled) {
            return;
        }
        --level;
    }

    private void logComment(String msg) {
        log.info(asComments ? "# " + msg : msg);
    }
}
