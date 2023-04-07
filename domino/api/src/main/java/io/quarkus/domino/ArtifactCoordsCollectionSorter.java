package io.quarkus.domino;

import com.fasterxml.jackson.databind.util.StdConverter;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ArtifactCoordsCollectionSorter extends StdConverter<Collection<ArtifactCoords>, List<String>> {
    @Override
    public List<String> convert(Collection<ArtifactCoords> value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return List.of();
        }
        final List<String> list = new ArrayList<>(value.size());
        for (ArtifactCoords c : value) {
            list.add(c.toGACTVString());
        }
        Collections.sort(list);
        return list;
    }
}
