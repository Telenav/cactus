package com.telenav.cactus.maven.model;

import java.util.Optional;

import static com.telenav.cactus.maven.model.VersionFlavor.RELEASE;
import static com.telenav.cactus.maven.model.VersionFlavor.SNAPSHOT;

/**
 * The manner in which a version flavor should be modified when creating a new
 * version.
 */
public enum VersionFlavorChange
{
    /**
     * If the version is release, make it snapshot, and vice-versa.
     */
    TO_OPPOSITE,
    /**
     * Force the new version to snapshot.
     */
    TO_SNAPSHOT,
    /**
     * Force the new version to release (no suffix).
     */
    TO_RELEASE,
    /**
     * Do nothing to the suffix.
     */
    UNCHANGED;

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    public static VersionFlavorChange between(VersionFlavor a, VersionFlavor b)
    {
        if (a == b)
        {
            return UNCHANGED;
        }
        if (a.opposite() == b)
        {
            return TO_OPPOSITE;
        }
        switch (b)
        {
            case SNAPSHOT:
                return TO_SNAPSHOT;
            case RELEASE:
                return TO_RELEASE;
            case OTHER:
                return UNCHANGED;
            default:
                throw new AssertionError(b);
        }
    }

    public boolean isNone()
    {
        return this == UNCHANGED;
    }

    /**
     * Compute the suffix for this flavor as applied to the passed version.
     *
     * @param version A version
     * @return A suffix
     */
    public Optional<String> newSuffix(PomVersion version)
    {
        switch (this)
        {
            case TO_OPPOSITE:
                return version.flavor().opposite().suffixFor(version);
            case TO_RELEASE:
                return RELEASE.suffixFor(version);
            case TO_SNAPSHOT:
                return SNAPSHOT.suffixFor(version);
            default:
                return version.suffix();
        }

    }
}
