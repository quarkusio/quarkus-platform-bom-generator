package io.quarkus.domino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertyResolverTest {

    @Test
    public void simpleString() {
        assertEquals("greeting", PropertyResolver.resolveProperty("greeting", Map.of("greeting", "hello")));
    }

    @Test
    public void simpleProperty() {
        assertEquals("hello", PropertyResolver.resolveProperty("${greeting}", Map.of("greeting", "hello")));
    }

    @Test
    public void simplePropertyInTheMiddle() {
        assertEquals("!!!hello!!!", PropertyResolver.resolveProperty("!!!${greeting}!!!", Map.of("greeting", "hello")));
    }

    @Test
    public void missingEndBrace() {
        assertEquals("${greeting", PropertyResolver.resolveProperty("${greeting", Map.of("greeting", "hello")));
    }

    @Test
    public void multiplePropsInSequence() {
        assertEquals("1+2=3",
                PropertyResolver.resolveProperty("${one}+${two}=${three}", Map.of("one", "1", "two", "2", "three", "3")));
    }

    @Test
    public void nestedProps() {
        assertEquals("3", PropertyResolver.resolveProperty("${${one}+${two}}", Map.of("one", "1", "two", "2", "1+2", "3")));
    }

    @Test
    public void missingEndBraceFollowedByLegitProp() {
        assertEquals("-${hello-you-", PropertyResolver.resolveProperty("-${hello-${hello}-", Map.of("hello", "you")));
    }

    @Test
    public void unknownSimpleRequiredProperty() {
        try {
            PropertyResolver.resolveProperty("${greeting}", Map.of("greet", "hello"));
            Assertions.fail("Should have failed");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void unknownSimpleOptionalProperty() {
        assertNull(PropertyResolver.resolvePropertyOrNull("${greeting}", Map.of("greet", "hello")));
    }

    @Test
    public void unknownSimplePropertyInTheMiddle() {
        assertNull(PropertyResolver.resolvePropertyOrNull("!!!${greeting}!!!", Map.of("greet", "hello")));
    }

    @Test
    public void simpleRecursive() {
        assertEquals("4.3.4", PropertyResolver.resolveProperty("${vertx.version}",
                Map.of("vertx.version", "${project.version}", "project.version", "4.3.4")));
    }

    @Test
    public void recursive() {
        assertEquals("1+4", PropertyResolver.resolveProperty("${top}",
                Map.of("top", "${one}+${two}",
                        "one", "1",
                        "two", "${three}",
                        "three", "${fou${r}}",
                        "r", "r",
                        "four", "4")));
    }

}
