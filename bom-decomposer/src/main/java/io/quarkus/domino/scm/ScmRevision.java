package io.quarkus.domino.scm;

import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import java.util.Objects;

public class ScmRevision implements ReleaseVersion, ReleaseId {

    public enum Kind {
        COMMIT,
        TAG,
        BRANCH,
        VERSION
    }

    public static ScmRevision tag(ScmRepository codeRepo, String tag) {
        return new ScmRevision(codeRepo, Kind.TAG, tag);
    }

    public static ScmRevision commit(ScmRepository codeRepo, String commit) {
        return new ScmRevision(codeRepo, Kind.COMMIT, commit);
    }

    public static ScmRevision branch(ScmRepository codeRepo, String branch) {
        return new ScmRevision(codeRepo, Kind.BRANCH, branch);
    }

    public static ScmRevision version(ScmRepository codeRepo, String version) {
        return new ScmRevision(codeRepo, Kind.VERSION, version);
    }

    private final ScmRepository repo;
    private final Kind kind;
    private final String value;

    private ScmRevision(ScmRepository codeRepo, Kind kind, String value) {
        this.repo = Objects.requireNonNull(codeRepo, "Code repository is missing");
        this.kind = Objects.requireNonNull(kind, "Revision kind is missing");
        this.value = Objects.requireNonNull(value, "Revision value is missing");
    }

    public ScmRepository getRepository() {
        return repo;
    }

    public Kind getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }

    @Override
    public ReleaseOrigin origin() {
        return repo;
    }

    @Override
    public ReleaseVersion version() {
        return this;
    }

    @Override
    public boolean isTag() {
        return kind == Kind.TAG;
    }

    @Override
    public String asString() {
        return getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ScmRevision revision = (ScmRevision) o;
        return Objects.equals(repo, revision.repo) && kind == revision.kind && Objects.equals(value, revision.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repo, kind, value);
    }

    @Override
    public String toString() {
        return repo.getId() + "@" + kind.name() + ":" + value;
    }
}
