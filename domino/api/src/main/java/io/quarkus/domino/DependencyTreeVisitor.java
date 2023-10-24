package io.quarkus.domino;

import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import org.eclipse.aether.repository.RemoteRepository;

public interface DependencyTreeVisitor {

    interface DependencyVisit {

        ScmRevision getRevision();

        ArtifactCoords getCoords();

        List<RemoteRepository> getRepositories();

        boolean isManaged();
    }

    void beforeAllRoots();

    void afterAllRoots();

    void enterRootArtifact(DependencyVisit visit);

    void leaveRootArtifact(DependencyVisit visit);

    void enterDependency(DependencyVisit visit);

    /**
     * In case the Maven artifact resolver was configured to return verbose dependency graphs,
     * this method will be called to indicate the current dependency graph node has a dependency
     * on another node with the passed in coordinates whose dependencies will be walked over
     * in a different branch of the graph.
     *
     * @param coords artifact coordinates of a dependency
     */
    default void linkDependency(ArtifactCoords coords) {
    }

    void leaveDependency(DependencyVisit visit);

    void enterParentPom(DependencyVisit visit);

    void leaveParentPom(DependencyVisit visit);

    void enterBomImport(DependencyVisit visit);

    void leaveBomImport(DependencyVisit visit);
}
