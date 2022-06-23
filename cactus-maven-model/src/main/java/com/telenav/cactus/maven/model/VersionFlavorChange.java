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
    TO_OPPOSITE,
    TO_SNAPSHOT,
    TO_RELEASE,
    UNCHANGED;

    public boolean isNone() {
        return this == UNCHANGED;
    }
    
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
