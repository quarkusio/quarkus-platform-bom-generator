package io.quarkus.bom.decomposer;

import io.quarkus.bom.PomResolver;
import io.quarkus.domino.scm.ScmRepository;
import io.quarkus.domino.scm.ScmRevision;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;

public class DecomposedBom {

    public class Builder {

        private Builder() {
        }

        public Builder bomSource(PomResolver bom) {
            bomSource = bom;
            return this;
        }

        public Builder bomArtifact(Artifact bom) {
            bomArtifact = bom;
            return this;
        }

        public Artifact getBomArtifact() {
            return bomArtifact;
        }

        public Builder addRelease(ProjectRelease release) {
            releases.computeIfAbsent(release.id().getRepository(), t -> new HashMap<>()).put(release.id(), release);
            return this;
        }

        public DecomposedBom build() {
            return DecomposedBom.this;
        }
    }

    public static Builder builder() {
        return new DecomposedBom().new Builder();
    }

    protected PomResolver bomSource;
    protected Artifact bomArtifact;
    protected Map<ScmRepository, Map<ScmRevision, ProjectRelease>> releases = new HashMap<>();

    private DecomposedBom() {
    }

    public String bomSource() {
        return bomSource == null ? "n/a" : bomSource.source();
    }

    public Artifact bomArtifact() {
        return bomArtifact;
    }

    public PomResolver bomResolver() {
        return bomSource;
    }

    public boolean includes(ScmRepository origin) {
        return releases.containsKey(origin);
    }

    public Collection<ScmRevision> releaseVersions(ScmRepository origin) {
        return releases.getOrDefault(origin, Map.of()).keySet();
    }

    public Collection<ProjectRelease> releases(ScmRepository origin) {
        return releases.getOrDefault(origin, Map.of()).values();
    }

    public ProjectRelease releaseOrNull(ScmRevision releaseId) {
        return releases.getOrDefault(releaseId.getRepository(), Map.of()).get(releaseId);
    }

    public Iterable<ProjectRelease> releases() {
        final Iterator<Map<ScmRevision, ProjectRelease>> origins = releases.values().iterator();
        return new Iterable<ProjectRelease>() {
            @Override
            public Iterator<ProjectRelease> iterator() {
                return new Iterator<ProjectRelease>() {
                    Iterator<ProjectRelease> releases;

                    @Override
                    public boolean hasNext() {
                        return releases != null && releases.hasNext() || origins.hasNext();
                    }

                    @Override
                    public ProjectRelease next() {
                        if (releases == null || !releases.hasNext()) {
                            releases = origins.next().values().iterator();
                        }
                        return releases.next();
                    }
                };
            }
        };
    }

    public void visit(DecomposedBomVisitor visitor) throws BomDecomposerException {
        visitor.enterBom(bomArtifact);
        List<ScmRepository> origins = new ArrayList<>(releases.keySet());
        for (ScmRepository origin : origins) {
            final Collection<ProjectRelease> releaseVersions = releases.get(origin).values();
            if (visitor.enterReleaseOrigin(origin, releaseVersions.size())) {
                for (ProjectRelease v : releaseVersions) {
                    visitor.visitProjectRelease(v);
                }
                visitor.leaveReleaseOrigin(origin);
            }
        }
        visitor.leaveBom();

    }
}
