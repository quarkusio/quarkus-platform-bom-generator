package io.quarkus.domino;

import java.util.List;
import java.util.Map;

public interface ProductReleaseNotes {

    String getType();

    String getTitle();

    List<String> getAliases();

    Map<String, String> getProperties();

    static Mutable builder() {
        return new ProductReleaseNotesImpl.Builder();
    }

    interface Mutable extends ProductReleaseNotes {

        Mutable setType(String type);

        Mutable setTitle(String title);

        Mutable setAliases(List<String> aliases);

        Mutable setProperties(Map<String, String> props);

        ProductReleaseNotes build();
    }

}
