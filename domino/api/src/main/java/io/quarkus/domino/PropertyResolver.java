package io.quarkus.domino;

import java.util.Map;

public final class PropertyResolver {

    public static String resolveProperty(String expr, Map<String, String> props) {
        return resolveProperty(expr, props, false);
    }

    public static String resolvePropertyOrNull(String expr, Map<String, String> props) {
        return resolveProperty(expr, props, true);
    }

    private static String resolveProperty(String expr, Map<String, String> props, boolean nullIfNotResolved) {
        StringBuilder sb = null;
        int i = 0;
        while (i < expr.length()) {
            var c = expr.charAt(i++);
            if (c == '$' && i + 1 < expr.length() && expr.charAt(i) == '{') {
                var r = resolveProperty(expr, i - 1, props, nullIfNotResolved);
                if (r.value == null) {
                    return null;
                }
                if (sb == null) {
                    if (i == 0 && r.endIndex == expr.length()) {
                        return r.value;
                    }
                    sb = new StringBuilder();
                    sb.append(expr.substring(0, i - 1));
                }
                sb.append(r.value);
                i = r.endIndex;
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? expr : sb.toString();
    }

    private static PropertyParsingResult resolveProperty(String s, int start, Map<String, String> props,
            boolean nullIfNotResolved) {
        final StringBuilder buf = new StringBuilder();
        int i = start + 2;
        while (i < s.length()) {
            final char c = s.charAt(i++);
            if (c == '$' && i + 1 < s.length() && s.charAt(i) == '{') {
                var result = resolveProperty(s, i - 1, props, nullIfNotResolved);
                if (result.value == null) {
                    return null;
                }
                i = result.endIndex;
                buf.append(result.value);
            } else if (c == '}') {
                String value = props.get(buf.toString());
                if (value == null && !nullIfNotResolved) {
                    throw new IllegalArgumentException(
                            "Failed to resolve " + buf + " with the following known properties " + props.keySet());
                }
                return PropertyParsingResult.of(value, i);
            } else {
                buf.append(c);
            }
        }
        if (buf.length() == 0) {
            return PropertyParsingResult.of(start == 0 ? s : s.substring(start), i);
        }
        return PropertyParsingResult.of("${" + buf, i);
    }

    private static class PropertyParsingResult {

        private static PropertyParsingResult of(String value, int endIndex) {
            return new PropertyParsingResult(value, endIndex);
        }

        final int endIndex;
        final String value;

        private PropertyParsingResult(String value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
        }
    }
}
