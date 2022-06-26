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

import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.scope.ProjectFamily;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.telenav.cactus.maven.versions.PropertyRole.FAMILY_PREV_VERSION;
import static com.telenav.cactus.maven.versions.PropertyRole.FAMILY_VERSION;
import static com.telenav.cactus.maven.versions.PropertyRole.PROJECT_PREV_VERSION;
import static com.telenav.cactus.maven.versions.PropertyRole.PROJECT_VERSION;

/**
 * Collections of VersionProperties that may be relevant to the task of changing
 * versions.
 */
final class VersionIndicatingProperties
{
    private final Set<VersionProperty<ProjectFamily>> familyVersionChanges;
    private final Set<VersionProperty<ProjectFamily>> familyPrevVersionChanges;
    private final Set<VersionProperty<MavenCoordinates>> projectVersionChanges;
    private final Set<VersionProperty<MavenCoordinates>> projectPrevVersionChanges;

    VersionIndicatingProperties(
            Set<VersionProperty<ProjectFamily>> familyVersionChanges,
            Set<VersionProperty<ProjectFamily>> familyPrevVersionChanges,
            Set<VersionProperty<MavenCoordinates>> projectVersionChanges,
            Set<VersionProperty<MavenCoordinates>> projectPrevVersionChanges)
    {
        this.familyVersionChanges = familyVersionChanges;
        this.familyPrevVersionChanges = familyPrevVersionChanges;
        this.projectVersionChanges = projectVersionChanges;
        this.projectPrevVersionChanges = projectPrevVersionChanges;
    }

    public static VersionIndicatingProperties create(
            PomCategories categories)
    {
        Map<String, ProjectFamily> familyVersionKeys = new HashMap<>();
        Map<String, ProjectFamily> familyPrevVersionKeys = new HashMap<>();
        Map<String, MavenCoordinates> projectVersionKeys = new HashMap<>();
        Map< String, MavenCoordinates> projectPrevVersionKeys = new HashMap<>();
        for (ProjectFamily fam : categories.pomsForFamily().keySet())
        {
            String prop = fam + ".version";
            familyVersionKeys.put(prop, fam);
            String prevProp = fam + ".prev.version";
            familyPrevVersionKeys.put(prevProp, fam);
            String prevProp2 = fam + ".previous.version";
            familyPrevVersionKeys.put(prevProp2, fam);
        }
        for (MavenCoordinates coords : categories.allCoordinates())
        {
            if (coords.artifactId().is(ProjectFamily.fromGroupId(coords
                    .groupId()).name()))
            {
                continue;
            }
            String dots = coords.artifactId().text().replace('-', '.');

            String prop = coords.artifactId() + ".version";
            String prevProp = coords.artifactId() + ".prev.version";
            String prevProp2 = coords.artifactId() + ".previous.version";
            ProjectFamily fam = ProjectFamily.fromGroupId(coords.groupId());
            String prop2 = fam + "." + prop;
            String prevProp3 = fam + "." + prevProp;
            String prevProp4 = fam + "." + prevProp2;

            String prop3 = dots + ".version";
            String prevProp5 = dots + ".prev.version";
            String prevProp6 = dots + ".previos.version";
            projectVersionKeys.put(prop, coords);
            projectVersionKeys.put(prop2, coords);
            projectVersionKeys.put(prop3, coords);
            projectPrevVersionKeys.put(prevProp, coords);
            projectPrevVersionKeys.put(prevProp2, coords);
            projectPrevVersionKeys.put(prevProp3, coords);
            projectPrevVersionKeys.put(prevProp4, coords);
            projectPrevVersionKeys.put(prevProp5, coords);
            projectPrevVersionKeys.put(prevProp6, coords);
        }
        Set<VersionProperty<ProjectFamily>> familyVersionChanges = collectPropertyChanges(
                categories, FAMILY_VERSION, familyVersionKeys);
        Set<VersionProperty<ProjectFamily>> familyPrevVersionChanges = collectPropertyChanges(
                categories, FAMILY_PREV_VERSION, familyPrevVersionKeys);
        Set<VersionProperty<MavenCoordinates>> projectVersionChanges = collectPropertyChanges(
                categories, PROJECT_VERSION, projectVersionKeys);
        Set<VersionProperty<MavenCoordinates>> projectPrevVersionChanges = collectPropertyChanges(
                categories, PROJECT_PREV_VERSION, projectPrevVersionKeys);
        return new VersionIndicatingProperties(familyVersionChanges,
                familyPrevVersionChanges, projectVersionChanges,
                projectPrevVersionChanges);
    }

    private static <T> Set<VersionProperty<T>> collectPropertyChanges(
            PomCategories categories,
            PropertyRole role,
            Map<String, T> props)
    {
        Set<VersionProperty<T>> changes = new HashSet<>();
        props.forEach((property, target) ->
        {
            Map<String, Set<Pom>> valuesAndPoms = categories
                    .pomsForValueOfProperty(property);
            valuesAndPoms.forEach((value, pomSet) ->
            {
                pomSet.forEach(pom ->
                {
                    VersionProperty<T> change = new VersionProperty<>(
                            property,
                            pom, target, value);
                    changes.add(change);
                });
            });
        });
        return changes;
    }

    Set<VersionProperty<?>> all()
    {
        Set<VersionProperty<?>> result = new HashSet<>(familyVersionChanges);
        result.addAll(familyPrevVersionChanges);
        result.addAll(projectVersionChanges);
        result.addAll(projectPrevVersionChanges);
        return result;
    }

    public void collectMatches(Pom pom,
            BiConsumer<PropertyRole, ? super VersionProperty<?>> into)
    {
        Predicate<VersionProperty<?>> test = prop -> prop.matches(pom);
        familyVersionChanges.stream().filter(test)
                .forEach(prop -> into.accept(PropertyRole.FAMILY_VERSION, prop));
        familyPrevVersionChanges.stream().filter(test)
                .forEach(prop -> into.accept(PropertyRole.FAMILY_PREV_VERSION,
                prop));
        projectVersionChanges.stream().filter(test)
                .forEach(prop -> into.accept(PropertyRole.PROJECT_VERSION, prop));
        projectPrevVersionChanges.stream().filter(test)
                .forEach(prop -> into.accept(PropertyRole.PROJECT_PREV_VERSION,
                prop));
    }

    public boolean isEmpty()
    {
        return familyVersionChanges.isEmpty() && familyPrevVersionChanges
                .isEmpty() && projectVersionChanges.isEmpty() && projectPrevVersionChanges
                .isEmpty();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        appendIfNotEmpty(PropertyRole.FAMILY_VERSION, familyVersionChanges, sb);
        appendIfNotEmpty(PropertyRole.FAMILY_PREV_VERSION,
                familyPrevVersionChanges, sb);
        appendIfNotEmpty(PropertyRole.PROJECT_VERSION, projectVersionChanges, sb);
        appendIfNotEmpty(PropertyRole.PROJECT_PREV_VERSION,
                projectPrevVersionChanges, sb);
        return sb.toString();
    }

    private void appendIfNotEmpty(PropertyRole heading,
            Set<? extends VersionProperty<?>> set, StringBuilder sb)
    {
        if (!set.isEmpty())
        {
            sb.append("  ").append(heading).append(":\n");
            set.forEach(change ->
            {
                sb.append("   * ").append(change).append('\n');
            });
        }
    }
}
