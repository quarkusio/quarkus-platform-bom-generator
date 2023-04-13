package io.quarkus.domino;

import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.Comparator;

public class ArtifactCoordsComparator implements Comparator<ArtifactCoords> {

    private static final ArtifactCoordsComparator INSTANCE = new ArtifactCoordsComparator();

    public static ArtifactCoordsComparator getInstance() {
        return INSTANCE;
    }

    private ArtifactCoordsComparator() {
    }

    @Override
    public int compare(ArtifactCoords o1, ArtifactCoords o2) {
        var i = o1.getGroupId().compareTo(o2.getGroupId());
        if (i == 0) {
            i = o1.getArtifactId().compareTo(o2.getArtifactId());
            if (i == 0) {
                i = o1.getClassifier().compareTo(o2.getClassifier());
                if (i == 0) {
                    i = o1.getType().compareTo(o2.getType());
                    if (i == 0) {
                        i = o1.getVersion().compareTo(o2.getVersion());
                    }
                }
            }
        }
        return i;
    }
}
