package io.quarkus.bom.decomposer.maven.playground;

import java.util.Collection;

public interface Element {

    /**
     * Element key.
     * 
     * @return element key
     */
    Object key();

    /**
     * Element version.
     * 
     * @return element version
     */
    Object version();

    /**
     * Members that provide the element.
     * 
     * @return members that provide the element
     */
    Collection<Member> members();
}
