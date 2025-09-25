package com.redhat.hacbs.recipes.scm;

import com.redhat.hacbs.recipes.GAV;

public interface ScmLocator {

    TagInfo resolveTagInfo(GAV toBuild);
}
