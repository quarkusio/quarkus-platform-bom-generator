package io.quarkus.bom.platform;

import io.quarkus.maven.dependency.ArtifactKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PlatformMemberExtensions {

    private final PlatformMember member;
    private final Map<ArtifactKey, ExtensionDeps> extDeps = new HashMap<>();

    PlatformMemberExtensions(PlatformMember member) {
        this.member = member;
    }

    void addExtension(ExtensionDeps ext) {
        for (ExtensionDeps e : extDeps.values()) {
            if (e.isRuntimeDep(ext.key())) {
                e.addExtensionDep(ext);
            } else if (ext.isRuntimeDep(e.key())) {
                ext.addExtensionDep(e);
            }
        }
        extDeps.put(ext.key(), ext);
    }

    ExtensionDeps getExtension(ArtifactKey extKey) {
        final ExtensionDeps ext = extDeps.get(extKey);
        if (ext == null) {
            throw new IllegalArgumentException(member.key() + " does not include extension " + extKey);
        }
        return ext;
    }

    Collection<ExtensionDeps> getFilteredOutExtensions() {
        final Collection<ArtifactKey> extCatalog = member.extensionCatalog();
        if (extCatalog.isEmpty()) {
            return Collections.emptyList();
        }
        final List<ExtensionDeps> list = new ArrayList<>(extDeps.size() - extCatalog.size());
        Map<ArtifactKey, List<ArtifactKey>> nonProdDeps = null;
        for (ExtensionDeps e : extDeps.values()) {
            if (!extCatalog.contains(e.key())) {
                list.add(e);
            } else {
                for (ArtifactKey d : e.getExtensionDeps()) {
                    if (!extCatalog.contains(d)) {
                        if (nonProdDeps == null) {
                            nonProdDeps = new TreeMap<>(ArtifactKeyComparator.getInstance());
                        }
                        nonProdDeps.computeIfAbsent(d, k -> new ArrayList<>()).add(e.key());
                    }
                }
            }
        }

        if (nonProdDeps != null) {
            for (Map.Entry<ArtifactKey, List<ArtifactKey>> e : nonProdDeps.entrySet()) {
                System.out.println("Not supported extension " + e.getKey() + " is a dependency of supported extensions:");
                Collections.sort(e.getValue(), ArtifactKeyComparator.getInstance());
                for (ArtifactKey k : e.getValue()) {
                    System.out.println(" - " + k);
                }
            }
        }
        return list;
    }

    private static class ArtifactKeyComparator implements Comparator<ArtifactKey> {

        private static ArtifactKeyComparator instance;

        static ArtifactKeyComparator getInstance() {
            return instance == null ? instance = new ArtifactKeyComparator() : instance;
        }

        @Override
        public int compare(ArtifactKey o1, ArtifactKey o2) {
            int i = o1.getGroupId().compareTo(o2.getGroupId());
            if (i != 0) {
                return i;
            }
            i = o1.getArtifactId().compareTo(o2.getArtifactId());
            if (i != 0) {
                return i;
            }
            i = o1.getClassifier().compareTo(o2.getClassifier());
            if (i != 0) {
                return i;
            }
            i = o1.getType().compareTo(o2.getType());
            if (i != 0) {
                return i;
            }
            return 0;
        }

    }
}
