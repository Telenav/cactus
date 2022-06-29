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

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

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
    private final Set<Pom> versionMismatches;
    private final VersionUpdateFilter filter;
    private boolean hasChanges;

    VersionChangeUpdatesCollector(
            Map<Pom, VersionChange> pomVersionChanges,
            Map<Pom, VersionChange> parentVersionChanges,
            Map<Pom, Set<PropertyChange<?, PomVersion>>> propertyChanges,
            Set<Pom> removeExplicitVersionFrom,
            Set<Pom> versionMismatches,
            VersionUpdateFilter filter)
    {
        this.pomVersionChanges = pomVersionChanges;
        this.parentVersionChanges = parentVersionChanges;
        this.propertyChanges = propertyChanges;
        this.removeExplicitVersionFrom = removeExplicitVersionFrom;
        this.versionMismatches = versionMismatches;
        this.filter = filter;
    }

    Map<Pom, VersionChange> pomVersionChanges()
    {
        return unmodifiableMap(pomVersionChanges);
    }

    Map<Pom, VersionChange> parentVersionChanges()
    {
        return unmodifiableMap(parentVersionChanges);
    }

    Set<Pom> versionMismatches()
    {
        return unmodifiableSet(versionMismatches);
    }

    Set<Pom> allChangedPoms()
    {
        Set<Pom> result = new HashSet<>(pomVersionChanges.keySet());
        result.addAll(parentVersionChanges.keySet());
        result.addAll(propertyChanges.keySet());
        return result;
    }

    Set<Pom> changedPomsInFamily(ProjectFamily family)
    {
        return allChangedPoms().stream().filter(pom -> family.is(pom)).collect(
                Collectors.toCollection(HashSet::new));
    }

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
        return result;
    }

    void removeVersionMismatch(Pom pom)
    {
        hasChanges |= versionMismatches.remove(pom);
    }

    ChangeResult removeExplicitVersionFrom(Pom pom)
    {
        boolean result = removeExplicitVersionFrom.add(pom);
        hasChanges |= result;
        return ChangeResult.changeResult(result);
    }

    ChangeResult removePomVersionChange(Pom pom)
    {
        VersionChange oldChange = pomVersionChanges.remove(notNull("pom",
                pom));
        boolean result = oldChange != null;
        hasChanges |= result;
        if (result)
        {
            System.out.println(
                    "REMOVE VER CHANGE " + pom.artifactId() + oldChange);

        }
        return ChangeResult.changeResult(result);
    }

    ChangeResult removeParentVersionChange(Pom pom)
    {
        VersionChange oldChange = parentVersionChanges.remove(notNull(
                "pom",
                pom));
        boolean result = oldChange != null;
        hasChanges |= result;
        if (result)
        {
            System.out.println("removeParentVersionChange " + pom);
        }
        return ChangeResult.changeResult(result);
    }

    ChangeResult changeProperty(Pom pom, PropertyChange<?, PomVersion> change)
    {
        if (!filter.shouldUpdateVersionProperty(pom, change.propertyName(),
                change.newValue(), change.oldValue()))
        {
            logFiltering(() -> "changeProperty blocked " + change.propertyName()
                    + " in "
                    + pom.toArtifactIdentifiers()
                    + " with " + change);

            return ChangeResult.FILTERED;
        }
        Set<PropertyChange<?, PomVersion>> changes = propertyChanges
                .computeIfAbsent(pom,
                        p -> new HashSet<>());
        ChangeResult result = ChangeResult.changeResult(changes.add(change));
        return result;
    }

    ChangeResult removeAllPropertyChanges(Pom pom)
    {
        Set<PropertyChange<?, PomVersion>> changes = propertyChanges.remove(pom);
        return ChangeResult.changeResult(changes != null && !changes.isEmpty());
    }

    ChangeResult addVersionMismatch(Pom pom)
    {
        if (versionMismatches.add(pom))
        {
            return ChangeResult.CHANGED;
        }
        return ChangeResult.ALREADY_PRESENT;
    }

    ChangeResult changePomVersion(Pom pom, VersionChange change)
    {
        if (!filter.shouldUpdatePomVersion(notNull("pom", pom),
                notNull("change", change)))
        {
            logFiltering(() -> "changeParentVersion blocked " + pom
                    .toArtifactIdentifiers()
                    + " with " + change);
            return ChangeResult.FILTERED;
        }
        VersionChange old = pomVersionChanges.put(pom, change);
        boolean was = hasChanges;
        boolean result = !Objects.equals(old, change);
        if (result)
        {
            versionMismatches.remove(pom);
        }
        hasChanges |= result;
        return ChangeResult.changeResult(result);
    }

    ChangeResult changeParentVersion(Pom pom, Pom parentPom,
            VersionChange change)
    {
        if (!filter.shouldUpdateParentVersion(notNull("pom", pom), notNull(
                "parentPom", parentPom), notNull("change", change)))
        {
            logFiltering(() -> "changeParentVersion blocked " + pom
                    .toArtifactIdentifiers()
                    + " parent " + parentPom.toArtifactIdentifiers() + " with " + change);
            return ChangeResult.FILTERED;
        }
        VersionChange old = parentVersionChanges.put(pom, change);
        boolean was = hasChanges;
        boolean result = !Objects.equals(old, change);
        if (result)
        {
            versionMismatches.remove(pom);
        }
        hasChanges |= result;
        return ChangeResult.changeResult(result);
    }

    private void logFiltering(Supplier<String> msg)
    {
        System.out.println(msg.get());
    }

    enum ChangeResult
    {
        CHANGED,
        ALREADY_PRESENT,
        FILTERED;

        static ChangeResult changeResult(boolean val)
        {
            return val
                   ? CHANGED
                   : ALREADY_PRESENT;
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
