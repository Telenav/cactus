package com.telenav.cactus.maven.model.published;

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
    
    public boolean differs() {
        return this == PUBLISHED_DIFFERENT;
    }

    public String toString()
    {
        return name().toLowerCase().replace('_', '-');
    }

}
