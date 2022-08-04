package com.telenav.cactus.maven.model;

import java.util.function.Function;

/**
 * A thing that wraps a string or strings, which may be placeholders or not yet
 * fully resolved (maven properties), representing an element in a pom file
 * (with or without substructure), and which can be passed a function to resolve
 * some or all of the values, for which it will return a new instance if the
 * function produces a change.
 *
 * @author Tim Boudreau
 */
public interface Resolvable<E extends Resolvable<E>> extends Comparable<E>
{

    /**
     * Resolve or refine the values represented by this object - typically
     * resolving property references with the string value.
     *
     * @param resolver A resolver function, which should, given the string
     * value, return a new string if it computes a change, or null if it cannot
     * resolve this object.
     * @return A new instance, if any change was made, otherwise this instance
     */
    E resolve(Function<String, String> resolver);

    /**
     * Determine if the value represented by this object is complete, or if it
     * needs to be resolved.
     *
     * @return true if this object is resolved
     */
    boolean isResolved();

    /**
     * Apply a series of resolver functions to this until a resolved instance is
     * returned.
     *
     * @param resolvers The resolvers
     * @return this, or a new instance
     */
    @SuppressWarnings("unchecked")
    default E resolve(Function<String, String>... resolvers)
    {
        E result = (E) this;
        if (!isResolved())
        {
            for (Function<String, String> resolver : resolvers)
            {
                result = result.resolve(resolver);
                if (isResolved())
                {
                    break;
                }
            }
        }
        return result;
    }
}
