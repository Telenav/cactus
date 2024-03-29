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

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.telenav.cactus.maven.refactoring.PomRole.*;
import static com.telenav.cactus.scope.ProjectFamily.familyOf;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

/**
 * Infers roles for POMs based on their type, parent, whether they contain
 * modules and similar, in order to make update decisions based on their role in
 * the meta-project.
 *
 * @author Tim Boudreau
 */
public final class PomCategorizer
{
    private final Map<PomRole, Set<Pom>> pomsForKind = new EnumMap<>(
            PomRole.class);
    private final Map<Pom, Set<PomRole>> rolesForPom = new HashMap<>();
    private final Map<ProjectFamily, Set<Pom>> pomsForFamily = new HashMap<>();
    private final Map<String, Map<String, Set<Pom>>> pomsForValueOfProperty = new HashMap<>();
    private final Set<MavenCoordinates> allCoordinates = new HashSet<>();
    private final Map<Pom, Pom> parentForPom = new HashMap<>();
    private final Map<Pom, Set<Pom>> childPomsByParent = new HashMap<>();

    private final Poms poms;

    public PomCategorizer(Poms poms)
    {
        this.poms = poms;
        categorizePoms();
    }

    public Set<ProjectFamily> families()
    {
        return pomsForFamily.keySet();
    }

    public boolean is(Pom pom, PomRole role)
    {
        Set<PomRole> forPom = rolesForPom.get(pom);
        return forPom == null
               ? false
               : forPom.contains(role);
    }

    public void eachPom(Consumer<Pom> c)
    {
        allPoms().forEach(c);
    }

    public List<Pom> allPoms()
    {
        return poms.poms();
    }

    public Map<String, Set<Pom>> pomsForValueOfProperty(String property)
    {
        return pomsForValueOfProperty.getOrDefault(property, emptyMap());
    }

    public Map<String, Map<String, Set<Pom>>> pomsForValueOfProperty()
    {
        return pomsForValueOfProperty;
    }

    public Set<MavenCoordinates> allCoordinates()
    {
        return allCoordinates;
    }

    public Map<Pom, Set<PomRole>> rolesForPom()
    {
        return rolesForPom;
    }

    public Map<ProjectFamily, Set<Pom>> pomsForFamily()
    {
        return pomsForFamily;
    }

    public void eachPomInFamily(ProjectFamily family, Consumer<Pom> c)
    {
        Set<Pom> result = pomsForFamily.get(family);
        if (result != null)
        {
            result.forEach(c);
        }
    }

    public Map<ProjectFamily, Set<Pom>> projectsInFamilyWithRole(PomRole role)
    {
        Map<ProjectFamily, Set<Pom>> result = new HashMap<>();
        pomsForFamily.forEach((family, poms) ->
        {
            Set<Pom> set = poms.stream().filter(pom -> is(pom, role)).collect(
                    Collectors.toCollection(HashSet::new));
            result.put(family, set);
        });
        return result;
    }

    public Set<Pom> pomsWithRoles(PomRole first, PomRole... more)
    {
        Set<Pom> result = new HashSet<>();
        eachPomWithRoleIn(result::add, first, more);
        return result;
    }

    public void eachPomWithRoleIn(Consumer<Pom> c, PomRole first,
            PomRole... more)
    {
        Set<PomRole> all = PomRole.setOf(first, more);
        for (Pom pom : poms)
        {
            Set<PomRole> roles = EnumSet.copyOf(rolesForPom.get(pom));
            roles.retainAll(all);
            if (!roles.isEmpty())
            {
                c.accept(pom);
            }
        }
    }

    public Set<Pom> pomsForFamily(ProjectFamily family)
    {
        return pomsForFamily.getOrDefault(family, emptySet());
    }

    public Set<PomRole> rolesFor(Pom pom)
    {
        return rolesForPom.getOrDefault(pom, Collections.emptySet());
    }

    public Poms poms()
    {
        return poms;
    }

    public boolean hasParent(Pom pom)
    {
        return parentForPom.containsKey(pom);
    }

    public Optional<Pom> parentOf(Pom pom)
    {
        return Optional.ofNullable(parentForPom.get(pom));
    }

    public void eachPomAndItsRoles(BiConsumer<Pom, Set<PomRole>> c)
    {
        PomRole.visitMapEntriesSorted(rolesForPom, c);
    }

    public ThrowingOptional<Pom> pomFor(MavenCoordinates coords)
    {
        return poms.get(coords);
    }

    public List<Pom> parents(Pom what)
    {
        List<Pom> result = new ArrayList<>();
        while (what != null)
        {
            result.add(what);
            what = parentForPom.get(what);
        }
        return result;
    }

    private void onPom(PomRole role, Pom pom)
    {
        Set<Pom> pomSet = pomsForKind
                .computeIfAbsent(role, r -> new HashSet<>());
        Set<PomRole> roles = rolesForPom.computeIfAbsent(pom,
                p -> new HashSet<>());
        pomSet.add(pom);
        roles.add(role);
    }

    public Set<Pom> childrenOf(Pom pom)
    {
        return childPomsByParent.getOrDefault(pom, emptySet());
    }

    public Set<Pom> descendantsOf(Pom pom)
    {
        Set<Pom> seen = new HashSet<>();
        Set<Pom> result = new HashSet<>();
        visitDescendantsOf(pom, seen, p ->
        {
            result.add(p);
            return true;
        });
        return result;
    }

    /**
     * Visit all descendants of a pom, aborting child trees if the passed
     * predicate returns false.
     *
     * @param pom A pom
     * @param addTo A predicate - if it returns false, descendants of the pom
     * passed will not be followed any further
     */
    public void visitDescendantsOf(Pom pom, Predicate<Pom> addTo)
    {
        visitDescendantsOf(pom, new HashSet<>(), addTo);
    }

    private void visitDescendantsOf(Pom pom, Set<Pom> seen, Predicate<Pom> addTo)
    {
        // In a mangled tree with cycles, which it is possible to encounter,
        // we could endlessly loop, so protect against that
        if (seen.contains(pom))
        {
            return;
        }
        seen.add(pom);
        childrenOf(pom).forEach(desc ->
        {
            if (addTo.test(desc))
            {
                visitDescendantsOf(desc, seen, addTo);
            }
        });
    }

    public Set<Pom> roots()
    {
        Set<Pom> result = new HashSet<>();
        for (Pom pom : allPoms())
        {
            if (!parentForPom.containsKey(pom))
            {
                result.add(pom);
            }
        }
        return result;
    }

    private void recordParent(Pom child, Pom parent)
    {
        parentForPom.put(child, parent);
        Set<Pom> all = childPomsByParent.computeIfAbsent(parent,
                p -> new HashSet<>());
        all.add(child);
    }

    private void categorizePoms()
    {
        // Collect the ways each pom is used within the project tree
        Set<MavenCoordinates> parents = new HashSet<>();
        for (Pom pom : poms.poms())
        {
            ThrowingOptional<ParentMavenCoordinates> parent = pom.parent();
            // Collect the actual parent POM
            parent.ifPresent(par ->
            {
                // Make sure we use the type with the right equality contract
                parents.add(par.toPlainMavenCoordinates());
                poms.get(par).ifPresent(
                        parentPom -> recordParent(pom, parentPom));
            });

            // Make sure 
            allCoordinates.add(pom.coordinates().toPlainMavenCoordinates());
            // Create an inverse index by property by property value to
            // map all POMs defining a property in relation to the name
            // and value
            pom.properties().forEach((prop, value) ->
            {
                Map<String, Set<Pom>> pomsByValue
                        = pomsForValueOfProperty.computeIfAbsent(prop,
                                p -> new HashMap<>());
                Set<Pom> set = pomsByValue.computeIfAbsent(value,
                        v -> new HashSet<>());
                set.add(pom);
            });
            // Collect the family
            ProjectFamily fam = familyOf(pom);
            Set<Pom> forFamily = pomsForFamily.computeIfAbsent(fam,
                    f -> new HashSet<>());
            forFamily.add(pom);
            // If it is a pom project...
            if (pom.isPomProject())
            {
                if (!pom.modules().isEmpty())
                {
                    // If it has modules, it is a bill of materials.
                    // We'll catch up with whether it is also supplying
                    // configuration below
                    onPom(BILL_OF_MATERIALS, pom);
                }
                else
                {
                    if (!parent.isPresent())
                    {
                        onPom(CONFIG_ROOT, pom);
                    }
                    else
                    {
                        onPom(CONFIG, pom);
                    }
                }
            }
            else
            {
                onPom(JAVA, pom);
            }
        }
        // Now mark all the poms referencecd by other poms as their parent
        // as having that category
        parents.forEach(parent ->
        {
            poms.get(parent).ifPresent(parentPom ->
            {
                onPom(PARENT, parentPom);
            });
        });

        // Note any UFOs so they don't disappear from our univers
        for (Pom pom : poms.poms())
        {
            Set<PomRole> roles = rolesForPom.get(pom);
            if (roles == null || roles.isEmpty())
            {
                onPom(UNKNOWN, pom);
            }
        }
        // If something is a parent AND a BILL_OF_MATERIALS (the typical maven
        // bill-of-materials-is-also-parent layout) then by definition, that
        // POM can also supply configuration to its children, so note that
        rolesForPom.forEach((pom, roles) ->
        {
            if (roles.contains(PARENT) && roles.contains(BILL_OF_MATERIALS))
            {
                if (pom.parent().isPresent())
                {
                    roles.add(CONFIG_ROOT);
                }
                else
                {
                    roles.add(CONFIG);
                }
            }
        });
    }
}
