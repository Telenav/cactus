package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;

/**
 *
 * @author Tim Boudreau
 */
public interface MavenVersioned
{
    ThrowingOptional<String> version();

    default boolean isResolved()
    {
        return version().isPresent()
                && PropertyResolver.isResolved(version().get());
    }
}
