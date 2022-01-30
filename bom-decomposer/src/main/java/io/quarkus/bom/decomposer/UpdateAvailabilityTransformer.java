package io.quarkus.bom.decomposer;

import io.quarkus.bom.decomposer.ProjectDependency.UpdateStatus;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.devtools.messagewriter.MessageWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;

public class UpdateAvailabilityTransformer implements DecomposedBomTransformer {

    private final ArtifactResolver resolver;
    private final MessageWriter log;

    public UpdateAvailabilityTransformer(ArtifactResolver resolver, MessageWriter log) {
        this.resolver = Objects.requireNonNull(resolver);
        this.log = Objects.requireNonNull(log);
    }

    @Override
    public DecomposedBom transform(DecomposedBom decomposedBom)
            throws BomDecomposerException {
        log.debug("Transforming decomposed %s", decomposedBom.bomArtifact());
        decomposedBom.visit(new NoopDecomposedBomVisitor(true) {

            List<ProjectRelease> releases = new ArrayList<>();

            @Override
            public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {

                // sort the collected release versions
                final List<ArtifactVersion> releaseVersions = new ArrayList<>();
                final Map<ArtifactVersion, ReleaseId> versionToReleaseId = new HashMap<>();
                for (ProjectRelease release : releases) {
                    for (String versionStr : release.artifactVersions()) {
                        final ArtifactVersion version = new DefaultArtifactVersion(versionStr);
                        releaseVersions.add(version);
                        final ReleaseId prevReleaseId = versionToReleaseId.put(version, release.id());
                        if (prevReleaseId != null) {
                            if (new DefaultArtifactVersion(prevReleaseId.version().asString())
                                    .compareTo(new DefaultArtifactVersion(release.id().version().asString())) > 0) {
                                versionToReleaseId.put(version, prevReleaseId);
                            }
                        }
                    }
                }
                Collections.sort(releaseVersions);

                for (ProjectRelease release : releases) {
                    for (ProjectDependency dep : release.dependencies()) {
                        // the latest version is the preferred one
                        int i = releaseVersions.size() - 1;
                        if (release.id().equals(versionToReleaseId.get(releaseVersions.get(i)))) {
                            dep.preferredVersion = true;
                            continue;
                        }

                        while (i >= 0) {
                            final ArtifactVersion version = releaseVersions.get(i--);
                            final ReleaseId releaseId = versionToReleaseId.get(version);
                            if (release.id().equals(releaseId)) {
                                // we've reached the release version the dep belongs to
                                break;
                            }
                            final Artifact updatedArtifact = dep.artifact().setVersion(version.toString());
                            if (isAvailable(updatedArtifact)) {
                                dep.setAvailableUpdate(
                                        ProjectDependency.create(releaseId, dep.dependency().setArtifact(updatedArtifact)));
                                break;
                            }
                        }

                        if (dep.updateStatus() == UpdateStatus.UNKNOWN) {
                            dep.setUpdateUnavailable();
                        }
                    }
                }
                releases.clear();
            }

            @Override
            public void visitProjectRelease(ProjectRelease release) {
                releases.add(release);
            }
        });
        log.debug("Transformed decomposed BOM %s", decomposedBom.bomArtifact());
        return decomposedBom;
    }

    private boolean isAvailable(Artifact artifact) {
        return resolver.resolveOrNull(artifact) != null;
    }
}
