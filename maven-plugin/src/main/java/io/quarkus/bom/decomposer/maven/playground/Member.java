package io.quarkus.bom.decomposer.maven.playground;

import java.util.Collection;

public interface Member extends ElementCatalog {

    /**
     * Member key
     * 
     * @return member key
     */
    Object key();

    /**
     * Member version
     * 
     * @return member version
     */
    Object version();

    /**
     * The version of the union at the time the member joined the union.
     * 
     * @return version of the union at the time the member joined the union
     */
    UnionVersion unionVersion();

    boolean containsAll(Collection<Object> elementKeys);
}
