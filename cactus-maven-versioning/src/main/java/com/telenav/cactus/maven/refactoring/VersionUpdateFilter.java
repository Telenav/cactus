package com.telenav.cactus.maven.refactoring;

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.Collection;
import java.util.function.Predicate;

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
                return test.test(ProjectFamily.fromGroupId(in.groupId()));
            }

            @Override
            public boolean shouldUpdatePomVersion(Pom in, VersionChange change)
            {
                return test.test(ProjectFamily.fromGroupId(in.groupId()));
            }
        };
    }
}
