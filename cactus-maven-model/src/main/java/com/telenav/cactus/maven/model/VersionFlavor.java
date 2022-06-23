package com.telenav.cactus.maven.model;

import java.util.Optional;

/**
 * Describes the kind of suffix a version has:
 * <ul>
 * <li>No suffix = RELEASE</li>
 * <li>-SNAPSHOT = SNAPSHOT</li>
 * <li>Anything else = OTHER</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public enum VersionFlavor
{
    /**
     * A snapshot version.
     */
    SNAPSHOT,
    /**
     * A release version (no suffix).
     */
    RELEASE,
    /**
     * A version which has a suffix after the decimals which is not the string
     * <code>-SNAPSHOT</code>.
     */
    OTHER;

    public boolean isSnapshot()
    {
        return this == SNAPSHOT;
    }

    public boolean isRelease()
    {
        return this == RELEASE;
    }

    public boolean isSuffixed()
    {
        return this != RELEASE;
    }

    /**
     * Get the opposite of this flavor - release for snapshot, snapshot for release.
     * OTHER has no opposite and returns itself.
     * 
     * @return A flavor
     */
    public VersionFlavor opposite()
    {
        switch (this)
        {
            case RELEASE:
                return SNAPSHOT;
            case SNAPSHOT:
                return RELEASE;
            default:
                return this;
        }
    }

    Optional<String> suffixFor(PomVersion ver)
    {
        switch (this)
        {
            case SNAPSHOT:
                return Optional.of("-SNAPSHOT");
            case RELEASE:
                return Optional.empty();
            default:
                return ver.suffix();
        }
    }

    /**
     * Get the version flavor for a string.
     * 
     * @param what A version string
     * @return A flavor
     */
    public static VersionFlavor of(String what)
    {
        return PomVersion.suffixOf(what)
                .map(sfx ->
                {
                    switch (sfx)
                    {
                        case "-SNAPSHOT":
                            return VersionFlavor.SNAPSHOT;
                        default:
                            return VersionFlavor.OTHER;
                    }
                }).orElse(VersionFlavor.RELEASE);
    }
}
