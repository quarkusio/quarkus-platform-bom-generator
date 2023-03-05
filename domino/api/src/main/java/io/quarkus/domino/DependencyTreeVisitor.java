package io.quarkus.domino;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import org.eclipse.aether.repository.RemoteRepository;

public interface DependencyTreeVisitor {

    interface DependencyVisit {

        ReleaseId getReleaseId();

        ArtifactCoords getCoords();

        List<RemoteRepository> getRepositories();

        boolean isManaged();
    }

    void beforeAllRoots();

    void afterAllRoots();

    void enterRootArtifact(DependencyVisit visit);

    void leaveRootArtifact(DependencyVisit visit);

    void enterDependency(DependencyVisit visit);

    void leaveDependency(DependencyVisit visit);

    void enterParentPom(DependencyVisit visit);

    void leaveParentPom(DependencyVisit visit);

    void enterBomImport(DependencyVisit visit);

    void leaveBomImport(DependencyVisit visit);
}
