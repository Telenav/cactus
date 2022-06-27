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
package com.telenav.cactus.maven.refactoring;

import com.telenav.cactus.maven.model.VersionChange;
import com.mastfrog.util.preconditions.Checks;
import com.telenav.cactus.maven.model.Pom;
import java.util.Map;
import java.util.Objects;

/**
 * Collector for version changes, which can report if any changes have accrued
 * and be reset, and ensures it is not reporting a change which was an attempt
 * to replace data with the same data.
 */
final class VersionChangeUpdatesCollector
{
    private final Map<Pom, VersionChange> pomVersionChanges;
    private final Map<Pom, VersionChange> parentVersionChanges;

    VersionChangeUpdatesCollector(Map<Pom, VersionChange> pomVersionChanges,
            Map<Pom, VersionChange> parentVersionChanges)
    {
        this.pomVersionChanges = pomVersionChanges;
        this.parentVersionChanges = parentVersionChanges;
    }
    boolean hasChanges;

    /**
     * Determine if this instance has made changes, and reset the record of that
     * to false.
     *
     * @return True if change methods were called and they <i>actually</i>
     * altered the stored data.
     */
    boolean hasChanges()
    {
        boolean result = hasChanges;
        hasChanges = false;
        if (result)
        {
            System.out.println("reset.");
        }
        return result;
    }

    void set()
    {
        hasChanges = true;
        System.out.println("Set.");
    }

    void or(boolean val)
    {
        if (val)
        {
            System.out.println("OR'd");
        }
        hasChanges |= val;
    }

    boolean removePomVersionChange(Pom pom)
    {
        VersionChange oldChange = pomVersionChanges.remove(Checks.notNull("pom",
                pom));
        boolean result = oldChange != null;
        hasChanges |= result;
        if (result)
        {
            System.out.println("removePomVersionChange " + pom);
        }
        return result;
    }

    boolean removeParentVersionChange(Pom pom)
    {
        VersionChange oldChange = parentVersionChanges.remove(Checks.notNull(
                "pom",
                pom));
        boolean result = oldChange != null;
        hasChanges |= result;
        if (result)
        {
            System.out.println("removeParentVersionChange " + pom);
        }
        return result;
    }

    boolean changePomVersion(Pom pom, VersionChange change)
    {
        VersionChange old = pomVersionChanges.put(Checks.notNull("pom", pom),
                Checks.notNull("change", change));
        boolean was = hasChanges;
        boolean result = !Objects.equals(old, change);
        hasChanges |= result;
        if (!was && result)
        {
            System.out.println(
                    "changePomVersion " + old + " -> " + change + " for " + pom);
        }
        return result;
    }

    boolean changeParentVersion(Pom pom, VersionChange change)
    {
        VersionChange old = parentVersionChanges.put(Checks.notNull("pom", pom),
                Checks.notNull("change", change));
        boolean was = hasChanges;
        boolean result = !Objects.equals(old, change);
        hasChanges |= result;
        if (!was && result)
        {
            System.out.println(
                    "changeParentVersion " + old + " -> " + change + " for " + pom);
        }
        return result;
    }

}
