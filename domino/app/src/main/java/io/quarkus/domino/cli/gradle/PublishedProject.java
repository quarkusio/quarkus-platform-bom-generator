package io.quarkus.domino.cli.gradle;

import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayList;
import java.util.List;

public class PublishedProject {

    private final List<ArtifactCoords> boms = new ArrayList<>();
    private final List<ArtifactCoords> libraries = new ArrayList<>();

    public void addBom(ArtifactCoords bom) {
        boms.add(bom);
    }

    public void addLibrary(ArtifactCoords library) {
        libraries.add(library);
    }

    public List<ArtifactCoords> getBoms() {
        return boms;
    }

    public List<ArtifactCoords> getLibraries() {
        return libraries;
    }

    public List<ArtifactCoords> getAllArtifacts() {
        if (boms.isEmpty()) {
            return libraries;
        }
        if (libraries.isEmpty()) {
            return boms;
        }
        final List<ArtifactCoords> r = new ArrayList<>(libraries.size() + boms.size());
        r.addAll(boms);
        r.addAll(libraries);
        return r;
    }
}
