package io.quarkus.domino.manifest;

import com.github.packageurl.PackageURL;
import io.quarkus.domino.scm.ScmRevision;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.List;
import org.eclipse.aether.repository.RemoteRepository;

interface VisitedComponent {

    ScmRevision getReleaseId();

    ArtifactCoords getArtifactCoords();

    List<RemoteRepository> getRepositories();

    List<VisitedComponent> getDependencies();

    PackageURL getPurl();

    String getBomRef();

}
