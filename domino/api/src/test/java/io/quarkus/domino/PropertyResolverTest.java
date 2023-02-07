package io.quarkus.domino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertyResolverTest {

    @Test
    public void simpleString() {
        assertEquals(PropertyResolver.resolveProperty("greeting", Map.of("greeting", "hello")), "greeting");
    }

    @Test
    public void simpleProperty() {
        assertEquals(PropertyResolver.resolveProperty("${greeting}", Map.of("greeting", "hello")), "hello");
    }

    @Test
    public void simplePropertyInTheMiddle() {
        assertEquals(PropertyResolver.resolveProperty("!!!${greeting}!!!", Map.of("greeting", "hello")), "!!!hello!!!");
    }

    @Test
    public void missingEndBrace() {
        assertEquals(PropertyResolver.resolveProperty("${greeting", Map.of("greeting", "hello")), "${greeting");
    }

    @Test
    public void multiplePropsInSequence() {
        assertEquals(PropertyResolver.resolveProperty("${one}+${two}=${three}", Map.of("one", "1", "two", "2", "three", "3")),
                "1+2=3");
    }

    @Test
    public void nestedProps() {
        assertEquals(PropertyResolver.resolveProperty("${${one}+${two}}", Map.of("one", "1", "two", "2", "1+2", "3")), "3");
    }

    @Test
    public void missingEndBraceFollowedByLegitProp() {
        assertEquals(PropertyResolver.resolveProperty("-${hello-${hello}-", Map.of("hello", "you")), "-${hello-you-");
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

}
