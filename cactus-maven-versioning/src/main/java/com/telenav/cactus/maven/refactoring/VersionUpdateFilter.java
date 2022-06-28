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
import java.util.Collection;
import java.util.function.Predicate;

import static com.telenav.cactus.scope.ProjectFamily.familyOf;
import static java.util.Collections.singleton;

/**
 * Filter which can turn off updating some (even necessary) properties, poms or
 * parents.
 *
 * @author Tim Boudreau
 */
public interface VersionUpdateFilter
{
    public static VersionUpdateFilter DEFAULT = new VersionUpdateFilter()
    {
    };

    default boolean shouldUpdateVersionProperty(Pom in, String propertyName,
            PomVersion newVersion, String currentValue)
    {
        return true;
    }

    default boolean shouldUpdatePomVersion(Pom in, VersionChange change)
    {
        return true;
    }

    default boolean shouldUpdateParentVersion(Pom in, Pom parent,
            VersionChange change)
    {
        return shouldUpdatePomVersion(parent, change);
    }

    public static VersionUpdateFilter withinFamily(ProjectFamily fam)
    {
        return withinFamilies(singleton(fam.name()));
    }

    public static VersionUpdateFilter withinFamilies(
            Collection<? extends String> families)
    {
        if (families.isEmpty())
        {
            return DEFAULT;
        }
        Predicate<ProjectFamily> test = fam ->
        {
            return families.contains(fam.name());
        };
        return new VersionUpdateFilter()
        {
            @Override
            public boolean shouldUpdateVersionProperty(Pom in,
                    String propertyName, PomVersion newVersion,
                    String currentValue)
            {
                return test.test(ProjectFamily.familyOf(in.groupId()));
            }

            @Override
            public boolean shouldUpdatePomVersion(Pom in, VersionChange change)
            {
                return test.test(ProjectFamily.familyOf(in.groupId()));
            }
        };
    }

    public static VersionUpdateFilter withinFamilyOrParentFamily(
            ProjectFamily family)
    {
        return withinFamiliesOrParentFamily(singleton(family.name()));
    }

    public static VersionUpdateFilter withinFamiliesOrParentFamily(
            Collection<? extends String> families)
    {
        if (families.isEmpty())
        {
            return DEFAULT;
        }
        Predicate<Pom> test = pom ->
        {
            ProjectFamily pf = familyOf(pom);
            boolean result = families.contains(familyOf(pom).name());
            if (!result)
            {
                for (String s : families)
                {
                    ProjectFamily f = ProjectFamily.named(s);
                    result |= pf.isParentFamilyOf(pom.groupId());
                    if (result)
                    {
                        break;
                    }
                }
            }
            return result;
        };

        return new VersionUpdateFilter()
        {
            @Override
            public boolean shouldUpdateVersionProperty(Pom in,
                    String propertyName, PomVersion newVersion,
                    String currentValue)
            {
                return test.test(in);
            }

            @Override
            public boolean shouldUpdatePomVersion(Pom in, VersionChange change)
            {
                return test.test(in);
            }
        };
    }

}
