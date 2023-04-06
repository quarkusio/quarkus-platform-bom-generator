package io.quarkus.domino;

import java.util.Collection;
import java.util.List;

public class ProjectDependencyConfigExcludeScopesFilter {

    private static final List<String> DEFAULT = List.of("test", "provided");

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Collection)) {
            return false;
        }
        final Collection<?> c = (Collection<?>) o;
        return c.size() == DEFAULT.size() && c.containsAll(DEFAULT);
    }
}
