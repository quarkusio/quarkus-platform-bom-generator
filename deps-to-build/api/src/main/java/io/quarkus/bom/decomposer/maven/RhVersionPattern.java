package io.quarkus.bom.decomposer.maven;

import io.quarkus.util.GlobUtil;
import java.util.regex.Pattern;

public class RhVersionPattern {

    private static final String RH_VERSION_SUFFIX = "?redhat-*";
    private static final Pattern RH_VERSION_SUFFIX_PATTERN = Pattern.compile(GlobUtil.toRegexPattern(RH_VERSION_SUFFIX));
    private static final String RH_VERSION_EXPR = "*redhat-*";
    private static final Pattern RH_VERSION_PATTERN = Pattern.compile(GlobUtil.toRegexPattern(RH_VERSION_EXPR));

    public static boolean isRhVersion(String version) {
        return RH_VERSION_PATTERN.matcher(version).matches();
    }

    public static String ensureNoRhSuffix(String version) {
        return RH_VERSION_SUFFIX_PATTERN.matcher(version).replaceFirst("");
    }
}
