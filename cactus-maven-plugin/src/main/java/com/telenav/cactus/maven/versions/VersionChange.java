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
package com.telenav.cactus.maven.versions;

import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Represents an old version to change from and a new version to change to.
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

    public VersionChangeMagnitude magnitude()
    {
        return VersionChangeMagnitude.between(oldVersion, newVersion);
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

    @Override
    public String toString()
    {
        return oldVersion + " -> " + newVersion;
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
        boolean oa = other.oldVersion().equals(oldVersion);
        boolean ob = other.newVersion().equals(newVersion);
        if (!oa || !ob) {
            if (other.toString().equals(toString())) {
                System.out.println("MISMATCH W SAME STRING '" + oa + "' '" + ob
                    + " for " + other.oldVersion() + " " + oldVersion() 
                + "' / '" + other.newVersion() + "' '" + newVersion() + "'");
            }
        }
        return other.oldVersion().equals(oldVersion)
                && other.newVersion.equals(newVersion);
    }

    @Override
    public int hashCode()
    {
        return newVersion.hashCode() + (71 * oldVersion.hashCode());
    }
}
