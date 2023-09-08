package io.quarkus.domino.cli.repo;

import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.Collection;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

public interface DependencyTreeVisitor<E> {

    interface DependencyTreeVisit<E> {

        DependencyNode getRoot();

        MessageWriter getLog();

        void pushEvent(E event);
    }

    void visitTree(DependencyTreeVisit<E> ctx);

    void onEvent(E event, MessageWriter log);

    void handleResolutionFailures(Collection<Artifact> artifacts);
}
