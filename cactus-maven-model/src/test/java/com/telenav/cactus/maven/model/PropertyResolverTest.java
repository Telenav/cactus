package com.telenav.cactus.maven.model;

import org.junit.jupiter.api.Test;

import static com.telenav.cactus.maven.model.PropertyResolver.isResolved;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author timb
 */
public class PropertyResolverTest
{

    @Test
    public void testIsResolved() {
        assertTrue(isResolved("abcd"));
        assertTrue(isResolved("1.2.3"));
        assertFalse(isResolved("some-${templated}-thing"));
        assertFalse(isResolved("${mastfrog.parent}"));
        assertTrue(isResolved("${blah${"));
        assertTrue(isResolved("}backwards${blee"));
        assertTrue(isResolved("${"));
        assertFalse(isResolved("${}"));
        assertFalse(isResolved("prefixed-${thing}"));
    }
}
