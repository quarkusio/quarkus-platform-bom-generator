package io.quarkus.domino.recipes.scm;

import io.quarkus.domino.recipes.GAV;

public interface ScmLocator {

    TagInfo resolveTagInfo(GAV toBuild);
}
