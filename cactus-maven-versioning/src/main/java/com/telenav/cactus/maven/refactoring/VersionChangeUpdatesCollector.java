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
import com.telenav.cactus.maven.model.PomVersion;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Collector for version changes, which can report if any changes have accrued
 * and be reset, and ensures it is not reporting a change which was an attempt
 * to replace data with the same data.
 */
final class VersionChangeUpdatesCollector
{
    private final Map<Pom, VersionChange> pomVersionChanges;
    private final Map<Pom, VersionChange> parentVersionChanges;
    private final Map<Pom, Set<PropertyChange<?, PomVersion>>> propertyChanges;
    private final Set<Pom> removeExplicitVersionFrom;
    private final VersionUpdateFilter filter;

    VersionChangeUpdatesCollector(
            Map<Pom, VersionChange> pomVersionChanges,
            Map<Pom, VersionChange> parentVersionChanges,
            Map<Pom, Set<PropertyChange<?, PomVersion>>> propertyChanges,
            Set<Pom> removeExplicitVersionFrom,
            VersionUpdateFilter filter)
    {
        this.pomVersionChanges = pomVersionChanges;
        this.parentVersionChanges = parentVersionChanges;
        this.propertyChanges = propertyChanges;
        this.removeExplicitVersionFrom = removeExplicitVersionFrom;
        this.filter = filter;
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

    ChangeResult removeExplicitVersionFrom(Pom pom)
    {
        boolean result = removeExplicitVersionFrom.add(pom);
        hasChanges |= result;
        return ChangeResult.of(result);
    }

    ChangeResult removePomVersionChange(Pom pom)
    {
        VersionChange oldChange = pomVersionChanges.remove(Checks.notNull("pom",
                pom));
        boolean result = oldChange != null;
        hasChanges |= result;
        return ChangeResult.of(result);
    }

    ChangeResult removeParentVersionChange(Pom pom)
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
        return ChangeResult.of(result);
    }

    ChangeResult changeProperty(Pom pom, PropertyChange<?, PomVersion> change)
    {
        if (!filter.shouldUpdateVersionProperty(pom, change.propertyName(),
                change.newValue(), change.oldValue()))
        {
            return ChangeResult.FILTERED;
        }
        Set<PropertyChange<?, PomVersion>> changes = propertyChanges
                .computeIfAbsent(pom,
                        p -> new HashSet<>());
        return ChangeResult.of(changes.add(change));
    }

    ChangeResult removeAllPropertyChanges(Pom pom)
    {
        Set<PropertyChange<?, PomVersion>> changes = propertyChanges.remove(pom);
        return ChangeResult.of(changes != null && !changes.isEmpty());
    }

    ChangeResult changePomVersion(Pom pom, VersionChange change)
    {
        if (!filter.shouldUpdatePomVersion(notNull("pom", pom),
                notNull("change", change)))
        {
            return ChangeResult.FILTERED;
        }
        VersionChange old = pomVersionChanges.put(pom, change);
        boolean was = hasChanges;
        boolean result = !Objects.equals(old, change);
        hasChanges |= result;
        if (!was && result)
        {
            System.out.println(
                    "changePomVersion " + old + " -> " + change
                    + " for " + pom);
        }
        return ChangeResult.of(result);
    }

    ChangeResult changeParentVersion(Pom pom, Pom parentPom, VersionChange change)
    {
        if (!filter.shouldUpdateParentVersion(notNull("pom", pom), notNull(
                "parentPom", parentPom), notNull("change", change)))
        {
            return ChangeResult.FILTERED;
        }
        VersionChange old = parentVersionChanges.put(pom, change);
        boolean was = hasChanges;
        boolean result = !Objects.equals(old, change);
        hasChanges |= result;
        if (!was && result)
        {
            System.out.println(
                    "changeParentVersion " + old + " -> " + change
                    + " for " + pom);
        }
        return ChangeResult.of(result);
    }

    enum ChangeResult
    {
        CHANGED,
        ALREADY_PRESENT,
        FILTERED;
        
        static ChangeResult of(boolean val) {
            return val ? CHANGED : ALREADY_PRESENT;
        }

        boolean isChange()
        {
            return this == CHANGED;
        }

        boolean isFiltered()
        {
            return this == FILTERED;
        }
    }
}
