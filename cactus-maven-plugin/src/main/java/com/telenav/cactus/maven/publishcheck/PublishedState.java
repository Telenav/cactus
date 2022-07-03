package com.telenav.cactus.maven.publishcheck;

/**
 * Publication state.
 *
 * @author Tim Boudreau
 */
public enum PublishedState
{
    /**
     * The artifact has been published, and the downloaded bits are identical.
     */
    PUBLISHED_IDENTICAL,
    /**
     * The artifact has been published, and the downloaded bits are different.
     */
    PUBLISHED_DIFFERENT,
    /**
     * The artifact has not been published.
     */
    NOT_PUBLISHED;

    public String toString()
    {
        return name().toLowerCase();
    }

}
