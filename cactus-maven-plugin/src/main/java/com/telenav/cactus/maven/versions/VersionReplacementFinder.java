package com.telenav.cactus.maven.versions;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.scope.ProjectFamily;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PomRole.*;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.FAMILY_PREV_VERSION;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.FAMILY_VERSION;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.PROJECT_PREV_VERSION;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.PROJECT_VERSION;
import static java.lang.Integer.max;
import static java.util.Collections.emptySet;

/**
 *
 * @author timb
 */
public class VersionReplacementFinder
{
    private final Poms poms;
    private final Set<GroupId> allGroupIds = new HashSet<>();
    private final Map<PomRole, Set<Pom>> pomsForKind = new EnumMap(PomRole.class);
    private final Map<Pom, Set<PomRole>> rolesForPom = new HashMap<>();
    private final Map<ProjectFamily, Set<Pom>> pomsForFamily = new HashMap<>();
    private final Map<String, Map<String, Set<Pom>>> pomsForValueOfProperty = new HashMap<>();
    private final Set<MavenCoordinates> allCoordinates = new HashSet<>();
    private final PotentialPropertyChanges propertyChanges;
    private final Map<ProjectFamily, VersionChange> familyVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> pomVersionChanges = new HashMap<>();
    private final boolean ignoreVersionMismatches;
    private final Set<Pom> versionMismatches = new HashSet<>();

    public VersionReplacementFinder(Poms poms, boolean ignoreVersionMismatches)
    {
        this.poms = poms;
        this.ignoreVersionMismatches = ignoreVersionMismatches;
        propertyChanges = categorizeProperties();
    }

    public static void main(String[] args) throws Exception
    {
        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff"));
        VersionReplacementFinder vr = new VersionReplacementFinder(poms, false)
                .withFamilyVersionChange(ProjectFamily.named("cactus"),
                        PomVersion.of("1.4.12"),
                        PomVersion.of("1.5.0"))
                .withFamilyVersionChange(ProjectFamily.named("kivakit"),
                        PomVersion.of("1.6.0"),
                        PomVersion.of("1.6.1")) //                .withFamilyVersionChange(ProjectFamily.named("lexakai"),
                //                        PomVersion.of("1.0.7"),
                //                        PomVersion.of("1.0.8"))
                ;
        System.out.println(vr);
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder("Version Replacements:\n");
        sb.append("FAMILIES:\n");
        familyVersionChanges.forEach((fam, ver) ->
        {
            sb.append(" * ").append(fam).append(" -> ").append(ver).append('\n');
        });
        sb.append("POMS:\n");
        pomVersionChanges.forEach((pom, ver) ->
        {
            sb.append(" * ").append(pom.coords.toArtifactIdentifiers())
                    .append(" -> ").append(ver).append('\n');
        });
        sb.append("PROPERTIES REPRESENTING VERSIONS:\n");
        sb.append(propertyChanges);

        // Sort for readability
        List<Map.Entry<Pom, Set<PomRole>>> entries = new ArrayList<>(rolesForPom
                .entrySet());
        entries.sort((a, b) ->
        {
            int result = Integer.compare(maxOrdinal(a.getValue()), maxOrdinal(b
                    .getValue()));
            if (result == 0)
            {
                result = a.getKey().compareTo(b.getKey());
            }
            return result;
        });

        sb.append("ROLES FOR POMS TO MODIFY:\n");
        entries.forEach((e) ->
        {
            Pom pom = e.getKey();
            Set<PomRole> roles = e.getValue();
            sb.append(" * ").append(pom.toArtifactIdentifiers()).append(' ')
                    .append(roles)
                    .append(versionMismatches.contains(pom)? " **VERSION-MISMATCH** " + pom.rawVersion() : "")
                    .append('\n');
        });

        return sb.toString();
    }

    private static <E extends Enum<E>> int maxOrdinal(Set<E> set)
    {
        int result = -1;
        for (E e : set)
        {
            result = max(result, e.ordinal());
        }
        return result;
    }

    public VersionReplacementFinder withFamilyVersionChange(ProjectFamily family,
            PomVersion old, PomVersion nue)
    {
        VersionChange vc = new VersionChange(old, nue);
        familyVersionChanges.put(family, vc);
        Set<Pom> poms = pomsForFamily.getOrDefault(family, emptySet());
        poms.forEach(pom ->
        {
            PomVersion currentVersion = pom.rawVersion();
            boolean match = currentVersion.equals(old);
            if (!match)
            {
                versionMismatches.add(pom);
                System.out.println(
                        "Skip version mismatch " + currentVersion
                        + " for " + pom);
            }
            if (ignoreVersionMismatches || match)
            {
                pomVersionChanges.put(pom, vc);
            }
        });
        return this;
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

    private void categorizePoms()
    {
        Set<MavenCoordinates> parents = new HashSet<>();
        for (Pom pom : poms.poms())
        {
            allGroupIds.add(pom.groupId());
            allCoordinates.add(pom.coords.toPlainMavenCoordinates());
            pom.properties().forEach((prop, value) ->
            {
                Map<String, Set<Pom>> pomsByValue
                        = pomsForValueOfProperty.computeIfAbsent(prop,
                                p -> new HashMap<>());
                Set<Pom> set = pomsByValue.computeIfAbsent(value,
                        v -> new HashSet<>());
                set.add(pom);
            });
            ProjectFamily fam = ProjectFamily.fromGroupId(pom.groupId());
            Set<Pom> forFamily = pomsForFamily.computeIfAbsent(fam,
                    f -> new HashSet<>());
            forFamily.add(pom);
            String pkg = pom.packaging;
            if ("pom".equals(pkg))
            {
                ThrowingOptional<ParentMavenCoordinates> parent = pom.parent();
                parent.ifPresent(par -> parents.add(par
                        .toPlainMavenCoordinates()));
                if (!pom.modules.isEmpty())
                {
                    onPom(BILL_OF_MATERIALS, pom);
                }
                else
                {
                    if (!parent.isPresent())
                    {
                        onPom(CONFIG_ROOT, pom);
                    }
                    onPom(CONFIG, pom);
                }
            }
            else
            {
                onPom(JAVA, pom);
            }
        }
        parents.forEach(parent ->
        {
            poms.get(parent).ifPresent(parentPom ->
            {
                onPom(PARENT, parentPom);
            });
        });
    }

    static class VersionChange
    {
        final PomVersion oldVersion;
        final PomVersion newVersion;

        public VersionChange(PomVersion oldVersion, PomVersion newVersion)
        {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        @Override
        public String toString()
        {
            return oldVersion + " -> " + newVersion;
        }
    }

    private PotentialPropertyChanges categorizeProperties()
    {
        categorizePoms();
        Map<String, ProjectFamily> familyVersionKeys = new HashMap<>();
        Map<String, ProjectFamily> familyPrevVersionKeys = new HashMap<>();
        Map<String, MavenCoordinates> projectVersionKeys = new HashMap<>();
        Map< String, MavenCoordinates> projectPrevVersionKeys = new HashMap<>();
        for (ProjectFamily fam : this.pomsForFamily.keySet())
        {
            String prop = fam + ".version";
            familyVersionKeys.put(prop, fam);
            String prevProp = fam + ".prev.version";
            familyPrevVersionKeys.put(prevProp, fam);
            String prevProp2 = fam + ".previous.version";
            familyPrevVersionKeys.put(prevProp2, fam);
        }
        for (MavenCoordinates coords : allCoordinates)
        {
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
        Set<VersionRepresentingProperty<ProjectFamily>> familyVersionChanges = collectPropertyChanges(
                FAMILY_VERSION, familyVersionKeys);
        Set<VersionRepresentingProperty<ProjectFamily>> familyPrevVersionChanges = collectPropertyChanges(
                FAMILY_PREV_VERSION, familyPrevVersionKeys);
        Set<VersionRepresentingProperty<MavenCoordinates>> projectVersionChanges = collectPropertyChanges(
                PROJECT_VERSION, projectVersionKeys);
        Set<VersionRepresentingProperty<MavenCoordinates>> projectPrevVersionChanges = collectPropertyChanges(
                PROJECT_PREV_VERSION, projectPrevVersionKeys);
        return new PotentialPropertyChanges(familyVersionChanges,
                familyPrevVersionChanges, projectVersionChanges,
                projectPrevVersionChanges);
    }

    static class PotentialPropertyChanges
    {
        final Set<VersionRepresentingProperty<ProjectFamily>> familyVersionChanges;
        final Set<VersionRepresentingProperty<ProjectFamily>> familyPrevVersionChanges;
        final Set<VersionRepresentingProperty<MavenCoordinates>> projectVersionChanges;
        final Set<VersionRepresentingProperty<MavenCoordinates>> projectPrevVersionChanges;

        public PotentialPropertyChanges(
                Set<VersionRepresentingProperty<ProjectFamily>> familyVersionChanges,
                Set<VersionRepresentingProperty<ProjectFamily>> familyPrevVersionChanges,
                Set<VersionRepresentingProperty<MavenCoordinates>> projectVersionChanges,
                Set<VersionRepresentingProperty<MavenCoordinates>> projectPrevVersionChanges)
        {
            this.familyVersionChanges = familyVersionChanges;
            this.familyPrevVersionChanges = familyPrevVersionChanges;
            this.projectVersionChanges = projectVersionChanges;
            this.projectPrevVersionChanges = projectPrevVersionChanges;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            appendIfNotEmpty(FAMILY_VERSION, familyVersionChanges, sb);
            appendIfNotEmpty(FAMILY_PREV_VERSION, familyPrevVersionChanges, sb);
            appendIfNotEmpty(PROJECT_VERSION, projectVersionChanges, sb);
            appendIfNotEmpty(PROJECT_PREV_VERSION, projectPrevVersionChanges, sb);
            return sb.toString();
        }

        private void appendIfNotEmpty(PropertyRole heading,
                Set<? extends VersionRepresentingProperty<?>> set,
                StringBuilder sb)
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

    private <T> Set<VersionRepresentingProperty<T>> collectPropertyChanges(PropertyRole role,
            Map<String, T> props)
    {
        Set<VersionRepresentingProperty<T>> changes = new HashSet<>();
        props.forEach((property, target) ->
        {
            Map<String, Set<Pom>> valuesAndPoms = pomsForValueOfProperty.get(
                    property);
            if (valuesAndPoms != null)
            {
                valuesAndPoms.forEach((value, pomSet) ->
                {
                    pomSet.forEach(pom ->
                    {
                        VersionRepresentingProperty<T> change = new VersionRepresentingProperty<>(property,
                                pom, target, value);
                        changes.add(change);
                    });
                });
            }
        });
        return changes;
    }

    private static final class VersionRepresentingProperty<T>
    {
        final String property;
        final Pom in;
        final T target;
        final String oldValue;

        public VersionRepresentingProperty(String property, Pom in, T target, String oldValue)
        {
            this.property = property;
            this.in = in;
            this.target = target;
            this.oldValue = oldValue;
        }

        @Override
        public String toString()
        {
            return property + " in " + target + " for " + in.pom + " was " + oldValue;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.property);
            hash = 97 * hash + Objects.hashCode(this.in);
            hash = 97 * hash + Objects.hashCode(this.target);
            return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final VersionRepresentingProperty<?> other = (VersionRepresentingProperty<?>) obj;
            if (!Objects.equals(this.property, other.property))
                return false;
            if (!Objects.equals(this.in, other.in))
                return false;
            return Objects.equals(this.target, other.target);
        }

    }

    enum PomRole
    {
        PARENT,
        BILL_OF_MATERIALS,
        CONFIG,
        CONFIG_ROOT,
        JAVA;

        boolean isPomProject()
        {
            return this != JAVA;
        }
    }

    enum PropertyRole
    {
        FAMILY_VERSION,
        FAMILY_PREV_VERSION,
        PROJECT_VERSION,
        PROJECT_PREV_VERSION,
        OTHER
    }
}
