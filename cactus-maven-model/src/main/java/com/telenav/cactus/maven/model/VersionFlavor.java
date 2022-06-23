package com.telenav.cactus.maven.model;

import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public enum VersionFlavor
{
    SNAPSHOT,
    RELEASE,
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

    public Optional<String> suffixFor(PomVersion ver)
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
