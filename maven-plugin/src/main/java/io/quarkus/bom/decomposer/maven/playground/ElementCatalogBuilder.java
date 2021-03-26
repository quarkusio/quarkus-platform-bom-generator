package io.quarkus.bom.decomposer.maven.playground;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ElementCatalogBuilder {

    public static ElementCatalogBuilder newInstance() {
        return new ElementCatalogBuilder();
    }

    public class ElementBuilder extends BuildCallback<Member> {

        private final Object key;
        private final Object version;
        private final List<BuildCallback<Element>> callbacks = new ArrayList<>(4);
        private final List<Member> members = new ArrayList<>();

        private ElementBuilder(Object key, Object version) {
            this.key = Objects.requireNonNull(key);
            this.version = Objects.requireNonNull(version);
        }

        private ElementBuilder addCallback(MemberBuilder callback) {
            callbacks.add(callback);
            callback.callbacks.add(this);
            return this;
        }

        private Element build() {
            final Element e = new Element() {
                @Override
                public Object key() {
                    return key;
                }

                @Override
                public Collection<Member> members() {
                    return members;
                }

                @Override
                public String toString() {
                    return key.toString() + "#" + version;
                }

                @Override
                public Object version() {
                    return version;
                }
            };
            callbacks.forEach(c -> c.created(e));
            return e;
        }

        @Override
        protected void created(Member t) {
            members.add(t);
        }
    }

    public class MemberBuilder extends BuildCallback<Element> {
        private final Object key;
        private final Object version;
        private UnionVersion unionVersion;
        private final List<BuildCallback<Member>> callbacks = new ArrayList<>();
        private final Map<Object, Element> elements = new HashMap<>();

        private MemberBuilder(Object key, Object version) {
            this.key = Objects.requireNonNull(key);
            this.version = Objects.requireNonNull(version);
        }

        public ElementBuilder addElement(Object elementKey) {
            return getOrCreateElement(elementKey, version).addCallback(this);
        }

        MemberBuilder addUnion(UnionBuilder union) {
            if (unionVersion == null) {
                unionVersion = union.version;
            }
            callbacks.add(union);
            return this;
        }

        @Override
        protected void created(Element t) {
            elements.put(t.key(), t);
        }

        public Member build() {
            final Member m = new Member() {

                @Override
                public Object key() {
                    return key;
                }

                @Override
                public Object version() {
                    return version;
                }

                @Override
                public UnionVersion unionVersion() {
                    return unionVersion;
                }

                @Override
                public Collection<Element> elements() {
                    return elements.values();
                }

                @Override
                public Collection<Object> elementKeys() {
                    return elements.keySet();
                }

                @Override
                public Element get(Object elementKey) {
                    return elements.get(elementKey);
                }

                @Override
                public String toString() {
                    return key.toString() + "#" + version + elements.values();
                }

                @Override
                public boolean containsAll(Collection<Object> elementKeys) {
                    return elements.keySet().containsAll(elementKeys);
                }
            };
            callbacks.forEach(c -> c.created(m));
            return m;
        }
    }

    public class UnionBuilder extends BuildCallback<Member> {

        private final UnionVersion version;
        private final List<Member> members = new ArrayList<>();

        private UnionBuilder(UnionVersion version) {
            this.version = Objects.requireNonNull(version);
        }

        public MemberBuilder addMember(Object memberKey, Object memberVersion) {
            return getOrCreateMember(memberKey, memberVersion).addUnion(this);
        }

        @Override
        protected void created(Member t) {
            members.add(t);
        }

        public Union build() {
            final Union u = new Union() {

                @Override
                public UnionVersion verion() {
                    return version;
                }

                @Override
                public Collection<Member> members() {
                    return members;
                }

                @Override
                public String toString() {
                    return version.toString() + members;
                }
            };
            return u;
        }
    }

    private abstract class BuildCallback<T> {

        protected abstract void created(T t);
    }

    static class IntVersion implements UnionVersion {

        static UnionVersion get(Integer i) {
            return new IntVersion(i);
        }

        private final Integer version;

        public IntVersion(int version) {
            this.version = version;
        }

        @Override
        public int compareTo(UnionVersion o) {
            if (o instanceof IntVersion) {
                return version.compareTo(((IntVersion) o).version);
            }
            throw new IllegalArgumentException(o + " is not an instance of " + IntVersion.class.getName());
        }

        @Override
        public String toString() {
            return version.toString();
        }
    }

    private final Map<Object, ElementBuilder> elements = new HashMap<>();
    private final Map<Object, MemberBuilder> members = new HashMap<>();
    private final Map<Comparable<?>, UnionBuilder> unions = new HashMap<>();

    private ElementBuilder getOrCreateElement(Object elementKey, Object elementVersion) {
        return elements.computeIfAbsent(elementKey, k -> new ElementBuilder(k, elementVersion));
    }

    private MemberBuilder getOrCreateMember(Object key, Object version) {
        return members.computeIfAbsent(key + ":" + version, k -> new MemberBuilder(key, version));
    }

    public UnionBuilder newUnion(int version) {
        return newUnion(IntVersion.get(version));
    }

    public UnionBuilder newUnion(UnionVersion version) {
        return unions.computeIfAbsent(version, v -> new UnionBuilder(version));
    }

    public ElementCatalog build() {

        final Map<Object, Element> map = new HashMap<>(elements.size());
        for (ElementBuilder e : elements.values()) {
            final Element build = e.build();
            map.put(build.key(), build);
        }
        for (MemberBuilder m : members.values()) {
            m.build();
        }
        for (UnionBuilder u : unions.values()) {
            u.build();
        }

        final ElementCatalog catalog = new ElementCatalog() {

            @Override
            public Collection<Element> elements() {
                return map.values();
            }

            @Override
            public Collection<Object> elementKeys() {
                return elements.keySet();
            }

            @Override
            public Element get(Object elementKey) {
                return map.get(elementKey);
            }

            @Override
            public String toString() {
                return elements.toString();
            }
        };

        return catalog;

    }
}
