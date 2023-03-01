package io.quarkus.domino;

import io.quarkus.devtools.messagewriter.MessageWriter;

public class LoggingDependencyTreeVisitor implements DependencyTreeVisitor {

    private static final String NOT_MANAGED = " [not managed]";

    private final MessageWriter log;
    private final boolean asComments;
    private int level;

    public LoggingDependencyTreeVisitor(MessageWriter log, boolean asComments) {
        this.log = log;
        this.asComments = asComments;
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        if (visit.isManaged()) {
            logComment(visit.getCoords().toCompactCoords());
        } else {
            logComment(visit.getCoords().toCompactCoords() + NOT_MANAGED);
        }
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        logComment("");
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
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
        --level;
    }

    @Override
    public void enterParentPom(DependencyVisit visit) {
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
        --level;
    }

    @Override
    public void enterBomImport(DependencyVisit visit) {
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
        --level;
    }

    private void logComment(String msg) {
        log.info(asComments ? "# " + msg : msg);
    }
}
