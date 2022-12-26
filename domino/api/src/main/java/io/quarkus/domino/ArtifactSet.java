package io.quarkus.domino;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A set of GAVs defined by includes and excludes {@link ArtifactCoordsPattern}s.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ArtifactSet {
    public static class Builder {
        private List<ArtifactCoordsPattern> excludes = new ArrayList<>();
        private List<ArtifactCoordsPattern> includes = new ArrayList<>();

        private Builder() {
        }

        public ArtifactSet build() {
            if (includes.isEmpty()) {
                includes.add(ArtifactCoordsPattern.matchAll());
            }

            List<ArtifactCoordsPattern> useIncludes = Collections.unmodifiableList(includes);
            List<ArtifactCoordsPattern> useExcludes = Collections.unmodifiableList(excludes);

            this.includes = null;
            this.excludes = null;

            return new ArtifactSet(useIncludes, useExcludes);
        }

        /**
         * Exclude a single GAV pattern.
         *
         * @param rawPattern raw pattern
         * @return this {@link Builder}
         */
        public Builder exclude(String rawPattern) {
            this.excludes.add(ArtifactCoordsPattern.of(rawPattern));
            return this;
        }

        public Builder exclude(ArtifactCoordsPattern pattern) {
            this.excludes.add(pattern);
            return this;
        }

        /**
         * Parses the entries of the given {@link Collection} of {@code rawPatterns} and excludes those.
         *
         * @param rawPatterns {@link Collection} of GAV patterns to parse via {@link ArtifactCoordsPattern#of(String)}
         * @return this {@link Builder}
         */
        public Builder excludes(Collection<String> rawPatterns) {
            if (rawPatterns != null) {
                for (String rawPattern : rawPatterns) {
                    this.excludes.add(ArtifactCoordsPattern.of(rawPattern));
                }
            }
            return this;
        }

        /**
         * Parses the entries of the given array of {@code rawPatterns} and excludes those.
         *
         * @param rawPatterns a list of GAV patterns to parse via {@link ArtifactCoordsPattern#of(String)}
         * @return this {@link Builder}
         */
        public Builder excludes(String... rawPatterns) {
            if (rawPatterns != null) {
                for (String rawPattern : rawPatterns) {
                    this.excludes.add(ArtifactCoordsPattern.of(rawPattern));
                }
            }
            return this;
        }

        /**
         * Parses the given comma or whitespace separated list of {@code rawPatterns} and excludes those.
         *
         * @param rawPatterns a comma separated list of GAV patterns
         * @return this {@link Builder}
         */
        public Builder excludes(String rawPatterns) {
            if (rawPatterns != null) {
                StringTokenizer st = new StringTokenizer(rawPatterns, ", \t\n\r\f");
                while (st.hasMoreTokens()) {
                    this.excludes.add(ArtifactCoordsPattern.of(st.nextToken()));
                }
            }
            return this;
        }

        /**
         * Adds {@link ArtifactCoordsPattern#matchSnapshots()} to {@link #excludes}.
         *
         * @return this {@link Builder}
         */
        public Builder excludeSnapshots() {
            this.excludes.add(ArtifactCoordsPattern.matchSnapshots());
            return this;
        }

        /**
         * Include a single GAV pattern.
         *
         * @param rawPattern raw pattern
         * @return this {@link Builder}
         */
        public Builder include(String rawPattern) {
            this.includes.add(ArtifactCoordsPattern.of(rawPattern));
            return this;
        }

        public Builder include(ArtifactCoordsPattern pattern) {
            this.includes.add(pattern);
            return this;
        }

        /**
         * Parses the entries of the given {@link Collection} of {@code rawPatterns} and includes those.
         *
         * @param rawPatterns {@link Collection} of GAV patterns to parse via {@link ArtifactCoordsPattern#of(String)}
         * @return this {@link Builder}
         */
        public Builder includes(Collection<String> rawPatterns) {
            if (rawPatterns != null) {
                for (String rawPattern : rawPatterns) {
                    this.includes.add(ArtifactCoordsPattern.of(rawPattern));
                }
            }
            return this;
        }

        /**
         * Parses the given comma or whitespace separated list of {@code rawPatterns} and includes those.
         *
         * @param rawPatterns a comma separated list of GAV patterns
         * @return this {@link Builder}
         */
        public Builder includes(String rawPatterns) {
            if (rawPatterns != null) {
                StringTokenizer st = new StringTokenizer(rawPatterns, ", \t\n\r\f");
                while (st.hasMoreTokens()) {
                    this.includes.add(ArtifactCoordsPattern.of(st.nextToken()));
                }
            }
            return this;
        }

        /**
         * Parses the entries of the given array of {@code rawPatterns} and includes those.
         *
         * @param rawPatterns a list of GAV patterns to parse via {@link ArtifactCoordsPattern#of(String)}
         * @return this {@link Builder}
         */
        public Builder includes(String... rawPatterns) {
            if (rawPatterns != null) {
                for (String rawPattern : rawPatterns) {
                    this.includes.add(ArtifactCoordsPattern.of(rawPattern));
                }
            }
            return this;
        }

    }

    private static final List<ArtifactCoordsPattern> EMPTY_LIST = Collections.emptyList();

    private static final ArtifactSet INCLUDE_ALL = new ArtifactSet(Collections.singletonList(ArtifactCoordsPattern.matchAll()),
            EMPTY_LIST);

    private static void append(List<ArtifactCoordsPattern> cludes, Appendable out) throws IOException {
        boolean first = true;
        for (ArtifactCoordsPattern gavPattern : cludes) {
            if (first) {
                first = false;
            } else {
                out.append(',');
            }
            out.append(gavPattern.toString());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ArtifactSet includeAll() {
        return INCLUDE_ALL;
    }

    private static boolean matches(
            String groupId,
            String artifactId,
            String type,
            String classifier,
            String version,
            List<ArtifactCoordsPattern> patterns) {
        for (ArtifactCoordsPattern pattern : patterns) {
            if (pattern.matches(groupId, artifactId, classifier, type, version)) {
                return true;
            }
        }
        return false;
    }

    private final List<ArtifactCoordsPattern> excludes;
    private final int hashcode;

    private final List<ArtifactCoordsPattern> includes;

    ArtifactSet(List<ArtifactCoordsPattern> includes, List<ArtifactCoordsPattern> excludes) {
        super();
        this.includes = includes;
        this.excludes = excludes;
        this.hashcode = 31 * (31 * 1 + excludes.hashCode()) + includes.hashCode();
    }

    /**
     * Appends {@link #excludes} to the given {@code out} separating them by comma.
     *
     * @param out an {@link Appendable} to append to
     * @throws IOException in case of a failure
     */
    public void appendExcludes(Appendable out) throws IOException {
        append(excludes, out);
    }

    /**
     * Appends {@link #includes} to the given {@code out} separating them by comma.
     *
     * @param out an {@link Appendable} to append to
     * @throws IOException in case of a failure
     */
    public void appendIncludes(Appendable out) throws IOException {
        append(includes, out);
    }

    /**
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @param type cannot be {@code null}
     * @param classifier can be {@code null}
     * @param version version
     * @return {@code true} if the given GAV triple is a member of this {@link ArtifactSet} and {@code false} otherwise
     */
    public boolean contains(String groupId, String artifactId, String type, String classifier, String version) {
        return matches(groupId, artifactId, type, classifier, version, includes)
                && !matches(groupId, artifactId, type, classifier, version, excludes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ArtifactSet other = (ArtifactSet) obj;
        if (excludes == null) {
            if (other.excludes != null)
                return false;
        } else if (!excludes.equals(other.excludes))
            return false;
        if (includes == null) {
            if (other.includes != null)
                return false;
        } else if (!includes.equals(other.includes))
            return false;
        return true;
    }

    /**
     * @return the list of excludes
     */
    public List<ArtifactCoordsPattern> getExcludes() {
        return excludes;
    }

    /**
     * @return the list of includes
     */
    public List<ArtifactCoordsPattern> getIncludes() {
        return includes;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public String toString() {
        return "GavSet [excludes=" + excludes + ", includes=" + includes + "]";
    }
}
