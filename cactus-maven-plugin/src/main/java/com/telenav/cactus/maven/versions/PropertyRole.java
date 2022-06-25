package com.telenav.cactus.maven.versions;

import com.telenav.cactus.maven.model.PomVersion;

/**
 * Roles we recognize for properties.
 *
 * @author timb
 */
enum PropertyRole
{
    FAMILY_VERSION,
    FAMILY_PREV_VERSION,
    PROJECT_VERSION,
    PROJECT_PREV_VERSION,
    OTHER;

    boolean isFamily()
    {
        return this == FAMILY_PREV_VERSION || this == FAMILY_VERSION;
    }

    boolean isProject()
    {
        return this == PROJECT_PREV_VERSION || this == PROJECT_VERSION;
    }

    boolean isPrevious()
    {
        return this == PROJECT_PREV_VERSION || this == FAMILY_PREV_VERSION;
    }

    PomVersion value(VersionChange change)
    {
        if (this == OTHER)
        {
            throw new IllegalStateException("Not a version property");
        }
        if (isPrevious())
        {
            return change.oldVersion();
        }
        else
        {
            return change.newVersion();
        }
    }

}
