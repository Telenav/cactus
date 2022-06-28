////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.model.VersionFlavor.SNAPSHOT;
import static java.util.Arrays.asList;

/**
 * Models a change from an old version to a new version.
 */
public final class VersionChange
{
    final PomVersion oldVersion;
    final PomVersion newVersion;

    public VersionChange(PomVersion oldVersion, PomVersion newVersion)
    {
        this.oldVersion = notNull("oldVersion", oldVersion);
        this.newVersion = notNull("newVersion", newVersion);
    }

    /**
     * Determine if this version change will reduce the version
     *
     * @return
     */
    public boolean isDowngrade()
    {
        if (!isChange())
        {
            return false;
        }
        List<PomVersion> both = new ArrayList<>(asList(oldVersion,
                newVersion));
        both.sort(Comparator.naturalOrder());
        boolean result = both.indexOf(newVersion) != 1;
        if (result && oldVersion.flavor() == SNAPSHOT && newVersion.flavor() != SNAPSHOT)
        {
            result = false;
        }
        return result;
    }

    /**
     * Safe factory method for version changes which only returns an instance if
     * the passed versions are not the same.
     *
     * @param oldVersion The old version
     * @param newVersion The new version
     * @return An optional VersionChange.
     */
    public static ThrowingOptional<VersionChange> versionChange(
            PomVersion oldVersion, PomVersion newVersion)
    {
        if (notNull("oldVersion", oldVersion).equals(notNull("newVersion",
                newVersion)))
        {
            return ThrowingOptional.empty();
        }
        return ThrowingOptional.of(new VersionChange(oldVersion, newVersion));
    }

    /**
     * Get the magnitude of this version change (will be NONE if no change).
     *
     * @return A magnitude
     */
    public VersionChangeMagnitude magnitudeChange()
    {
        return VersionChangeMagnitude.between(oldVersion, newVersion);
    }

    /**
     * Get the suffix change for this version change.
     *
     * @return A VersionFlavorChange describing any changes to the suffix (will
     * may be UNCHANGED if no change)
     */
    public VersionFlavorChange flavorChange()
    {
        return VersionFlavorChange.between(oldVersion.flavor(),
                newVersion.flavor());
    }

    /**
     * Determine if this represents a change, or if the versions contained
     * internally are the same.
     *
     * @return True if the versions differ
     */
    public boolean isChange()
    {
        return !oldVersion.equals(newVersion);
    }

    /**
     * The version to change from.
     *
     * @return A version
     */
    public PomVersion oldVersion()
    {
        return oldVersion;
    }

    /**
     * The version to change to.
     *
     * @return A version
     */
    public PomVersion newVersion()
    {
        return newVersion;
    }

    public static ThrowingOptional<VersionChange> parse(String what)
    {
        if (what == null || what.isEmpty())
        {
            return ThrowingOptional.empty();
        }
        String[] parts = what.split("->");
        if (parts.length != 2)
        {
            return ThrowingOptional.empty();
        }
        PomVersion from = PomVersion.of(parts[0]);
        PomVersion to = PomVersion.of(parts[1]);
        return ThrowingOptional.of(new VersionChange(from, to));
    }

    @Override
    public String toString()
    {
        return oldVersion + "->" + newVersion;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != VersionChange.class)
            {
                return false;
            }
        VersionChange other = (VersionChange) o;
        return other.oldVersion().equals(oldVersion)
                && other.newVersion.equals(newVersion);
    }

    @Override
    public int hashCode()
    {
        return newVersion.hashCode() + (71 * oldVersion.hashCode());
    }
}
