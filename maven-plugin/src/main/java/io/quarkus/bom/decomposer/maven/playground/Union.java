package io.quarkus.bom.decomposer.maven.playground;

import java.util.Collection;

public interface Union {

    /**
     * Union version.
     * 
     * @return union version
     */
    UnionVersion verion();

    /**
     * Members of the union.
     * 
     * @return members of the union
     */
    Collection<Member> members();
}
