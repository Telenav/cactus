package com.telenav.cactus.maven.versions;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.scope.ProjectFamily;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PomRole.*;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.FAMILY_PREV_VERSION;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.FAMILY_VERSION;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.PROJECT_PREV_VERSION;
import static com.telenav.cactus.maven.versions.VersionReplacementFinder.PropertyRole.PROJECT_VERSION;
import static java.lang.Integer.max;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.emptySet;

/**
 * Accepts a mapping of versions to change for individual POM files and/or
 * families, and will pinpoint all of the files that need changes, including
 * property updates as long as they follow one of the following patterns:
 * <ul>
 * <li>$FAMILY.version</li>
 * <li>$FAMILY.prev.version</li>
 * <li>$FAMILY.previous.version</li>
 * <li>$ARTIFACT_ID.version</li>
 * <li>s/$ARTIFACT_ID/-/..version</li>
 * <li>$FAMILY.$ARTFACT_ID.version</li>
 * <li>$ARTIFACT_ID.prev.version</li>
 * <li>s/$ARTIFACT_ID/-/..prev.version</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public class VersionReplacementFinder
{
    private final Poms poms;
    private final Set<GroupId> allGroupIds = new HashSet<>();
    private final Map<PomRole, Set<Pom>> pomsForKind = new EnumMap<>(
            PomRole.class);
    private final Map<Pom, Set<PomRole>> rolesForPom = new HashMap<>();
    private final Map<ProjectFamily, Set<Pom>> pomsForFamily = new HashMap<>();
    private final Map<String, Map<String, Set<Pom>>> pomsForValueOfProperty = new HashMap<>();
    private final Set<MavenCoordinates> allCoordinates = new HashSet<>();
    private final PotentialPropertyChanges propertyChanges;
    private final Map<ProjectFamily, VersionChange> familyVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> pomVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> parentVersionChanges = new HashMap<>();
    private final Map<Pom, Pom> parentForPom = new HashMap<>();
    private final boolean ignoreVersionMismatches;
    private final Set<Pom> versionMismatches = new HashSet<>();
    private boolean bumpVersionsOfSuperpoms;

    public VersionReplacementFinder(Poms poms, boolean ignoreVersionMismatches)
    {
        this.poms = poms;
        this.ignoreVersionMismatches = ignoreVersionMismatches;
        propertyChanges = categorizeProperties();
    }

    public VersionReplacementFinder bumpVersionsOfSuperpoms()
    {
        bumpVersionsOfSuperpoms = true;
        return this;
    }

    public static void main(String[] args) throws Exception
    {
        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff"));
//        Poms poms = Poms.in(Paths.get("/tmp/jonstuff"));
        VersionReplacementFinder vr = new VersionReplacementFinder(poms, false)
                .bumpVersionsOfSuperpoms()
                .withFamilyVersionChange(ProjectFamily.named("cactus"),
                        PomVersion.of("1.4.12"),
                        PomVersion.of("1.4.13"))
//                .withFamilyVersionChange(ProjectFamily.named("kivakit"),
//                        PomVersion.of("1.6.0"),
//                        PomVersion.of("1.6.2-SNAPSHOT"))
                .withFamilyVersionChange(ProjectFamily.named("lexakai"),
                        PomVersion.of("1.0.7"),
                        PomVersion.of("1.0.8"));
        System.out.println(vr);
        vr.go();
    }

    private void resolveVersionMismatchesAndFinalizeUpdateSet()
    {
        if (!versionMismatches.isEmpty() && bumpVersionsOfSuperpoms)
        {
            for (Iterator<Pom> it = versionMismatches.iterator(); it.hasNext();)
            {
                Pom pom = it.next();
                Set<PomRole> roles = rolesForPom.get(pom);
                if (roles.contains(CONFIG) || roles.contains(CONFIG_ROOT))
                {
                    it.remove();
                    ProjectFamily family = ProjectFamily.fromGroupId(pom
                            .groupId());
                    VersionChange familyChange = familyVersionChanges
                            .get(family);
                    VersionFlavorChange flavorChange = VersionFlavorChange.UNCHANGED;
                    VersionChangeMagnitude bumpMagnitude = VersionChangeMagnitude.DOT;
                    if (familyChange != null)
                    {
                        flavorChange = familyChange.newVersion.flavor().toThis();
                        bumpMagnitude = VersionChangeMagnitude.between(
                                familyChange.oldVersion, familyChange.newVersion);
                    }

                    PomVersion newVersion = pom.rawVersion().updatedWith(
                            VersionChangeMagnitude.DOT, flavorChange).get();
                    VersionChange vc = new VersionChange(pom.rawVersion(),
                            newVersion);
                    if (!pom.hasExplicitVersion())
                    {
                        List<Pom> parents = pom.parents(poms);
                        for (Pom par : parents)
                        {
                            if (par.rawVersion().equals(pom.rawVersion()))
                            {
                                if (par.hasExplicitVersion())
                                {
                                    pomVersionChanges.put(par, vc);
                                }
                                else
                                {
                                    parentVersionChanges.put(par, vc);
                                }
                            }
                        }
                    }
                    else
                    {
                        pomVersionChanges.put(pom, vc);
                    }
                }
                else
                {
                    System.out.println("RETAIN VERSION MISMATCH " + pom);
                }
            }
        }
        for (Pom pom : poms.poms())
        {
            Pom parentPom = parentForPom.get(pom);

            if (parentPom != null)
            {
                VersionChange ch = pomVersionChanges.get(parentPom);
                if (ch == null && !parentPom.hasExplicitVersion())
                {
                    ch = parentVersionChanges.get(parentPom);
                }
                parentVersionChanges.put(pom, ch);
            }
            if (!pom.hasExplicitVersion())
            {
                pomVersionChanges.remove(pom);
            }
        }
    }

    private List<TextContentReplacer> replacers()
    {
        resolveVersionMismatchesAndFinalizeUpdateSet();
        List<TextContentReplacer> replacers = new ArrayList<>();
        for (Pom pom : poms.poms())
        {
            VersionChange vc = this.pomVersionChanges.get(pom);
            Set<VersionRepresentingProperty<?>> matched = new HashSet<>();
            propertyChanges.collectMatches(pom, (kind, prop) ->
            {
                matched.add(prop);
                String newValue = null;
                if (kind.isFamily())
                {
                    ProjectFamily fam = (ProjectFamily) prop.target;
                    VersionChange fv = this.familyVersionChanges.get(fam);
                    if (fv != null)
                    {
                        newValue = kind.value(fv).text();
                    }
                    else
                    {
                        System.out.println(
                                "no new value for " + prop + " in " + pom
                                        .artifactId());
                    }
                }
                else
                {
                    MavenCoordinates coords = (MavenCoordinates) prop.target;
                    Pom target = poms.get(coords).get();
                    assert coords.equals(target.coords);
                    VersionChange cd = this.pomVersionChanges.get(target);
                    if (cd != null)
                    {
                        newValue = kind.value(cd).text();
                    }
                    else
                    {
                        System.out.println(
                                "no new value for " + prop + " in " + pom
                                        .artifactId());
                    }
                }
                if (newValue != null)
                {
                    String query = "project/properties/" + prop.property;
                    replacers.add(
                            new TextContentReplacer(PomFile.of(pom), query,
                                    newValue));
                }
            });
            Set<VersionRepresentingProperty<?>> neverMatched = propertyChanges
                    .all();
            neverMatched.removeAll(matched);
            if (!neverMatched.isEmpty())
            {
                System.out.println("\nSOME UNMATCHED PROPERTIES:");
                neverMatched.forEach(n -> System.out.println("  * " + n));
            }
            if (vc != null)
            {
                String query = "/project/version";
                replacers.add(new TextContentReplacer(PomFile.of(pom), query,
                        vc.newVersion.text()));
            }
            VersionChange parentChange = parentVersionChanges.get(pom);
            if (parentChange != null)
            {
                String query = "/project/parent/version";
                replacers.add(new TextContentReplacer(PomFile.of(pom), query,
                        parentChange.newVersion.text()));
            }
        }
        return replacers;
    }

    public Set<Path> go() throws Exception
    {

        List<TextContentReplacer> replacers = replacers();
        return TextContentReplacer.openAll(replacers, () ->
        {
            Set<Path> result = new HashSet<>();
            Map<Path, Document> docForPath = new HashMap<>();
            for (TextContentReplacer rep : replacers)
            {
                Document changed = rep.replace();
                if (changed != null)
                {
                    Document old = docForPath.get(rep.path());
                    if (old != changed && old != null)
                    {
                        throw new IllegalStateException(
                                "Context did not hold - " + old + " vs " + changed + " for " + rep
                                        .path());
                    }
                    System.out.println(" CHANGE: " + rep);
                    docForPath.put(rep.path(), changed);
                }
            }
            for (Map.Entry<Path, Document> e : docForPath.entrySet())
            {
                writeXML(e.getValue(), e.getKey());
                result.add(e.getKey());
            }
            return result;
        });
    }

    private static void writeXML(Document doc, Path path) throws Exception
    {
        String oldContent = Files.readString(path, UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamResult res = new StreamResult(out);
        TransformerFactory tFactory
                = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
//        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(
                "{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), res);
        String munge = new String(out.toByteArray(), "UTF-8");
        munge = restoreOriginalHead(oldContent, munge);
        Files.write(path, munge.getBytes(UTF_8), WRITE, TRUNCATE_EXISTING);
        System.out.println("Saved " + path);
    }

    private static String restoreOriginalHead(String orig, String mangled)
    {
        int oix = orig.indexOf("<project");
        int nix = mangled.indexOf("<project");
        if (oix < 0 || nix < 0)
        {
            // If it's hosed, don't make it worse.
            return mangled;
        }
        int oend = orig.indexOf('>', oix + 1);
        int nend = mangled.indexOf('>', nix + 1);
        if (oend < 0 || nend < 0)
        {
            return mangled;
        }
        String oldHead = orig.substring(0, oend + 1);
        String newTail = mangled.substring(nend + 1, mangled.length());
        return oldHead + newTail + (newTail.charAt(newTail.length() - 1) == '\n'
                                    ? ""
                                    : '\n');
    }

    @Override
    public String toString()
    {
        List<TextContentReplacer> replacers = replacers();
        StringBuilder sb = new StringBuilder("Version Replacements:\n");
        sb.append("FAMILIES:\n");
        familyVersionChanges.forEach((fam, ver) ->
        {
            sb.append(" * ").append(fam).append(" -> ").append(ver).append('\n');
        });
        if (!pomVersionChanges.isEmpty())
        {
            sb.append("POMS:\n");
            pomVersionChanges.forEach((pom, ver) ->
            {
                sb.append(" * ").append(pom.coords.toArtifactIdentifiers())
                        .append(" -> ").append(ver).append('\n');
            });
        }

        if (!parentVersionChanges.isEmpty())
        {
            sb.append("PARENT CHANGES:\n");
            parentVersionChanges.forEach((pom, ver) ->
            {
                sb.append(" * ").append(pom.coords.toArtifactIdentifiers())
                        .append(" ->-> ").append(ver).append('\n');
            });
        }

        if (!propertyChanges.isEmpty())
        {
            sb.append("PROPERTIES REPRESENTING VERSIONS:\n");
            sb.append(propertyChanges);
        }

        if (!rolesForPom.isEmpty())
        {
            // Sort for readability
            List<Map.Entry<Pom, Set<PomRole>>> entries = new ArrayList<>(
                    rolesForPom
                            .entrySet());
            entries.sort((a, b) ->
            {
                int result = Integer.compare(maxOrdinal(a.getValue()),
                        maxOrdinal(b
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
                        .append(versionMismatches.contains(pom)
                                ? " **VERSION-MISMATCH** " + pom.rawVersion()
                                : "")
                        .append('\n');
            });
        }
        if (!replacers.isEmpty())
        {
            sb.append("\n-------------- REPLACERS ----------------\n");
            replacers.forEach(rep -> sb.append(" * ").append(rep).append("\n"));
        }

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
            pom.parent().ifPresent(parent ->
            {
                poms.get(parent).ifPresent(parentPom -> parentForPom.put(pom,
                        parentPom));
            });
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
            return other.oldVersion.equals(oldVersion) && other.newVersion
                    .equals(newVersion);
        }

        public int hashCode()
        {
            return newVersion.hashCode() + (71 * oldVersion.hashCode());
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

        Set<VersionRepresentingProperty<?>> all()
        {
            Set<VersionRepresentingProperty<?>> result = new HashSet<>(
                    familyVersionChanges);
            result.addAll(familyPrevVersionChanges);
            result.addAll(projectVersionChanges);
            result.addAll(projectPrevVersionChanges);
            return result;
        }

        public void collectMatches(Pom pom,
                BiConsumer<PropertyRole, ? super VersionRepresentingProperty<?>> into)
        {
            Predicate<VersionRepresentingProperty<?>> test = prop -> prop
                    .matches(pom);
            familyVersionChanges.stream().filter(test).forEach(prop -> into
                    .accept(FAMILY_VERSION, prop));
            familyPrevVersionChanges.stream().filter(test).forEach(prop -> into
                    .accept(FAMILY_PREV_VERSION, prop));
            projectVersionChanges.stream().filter(test).forEach(prop -> into
                    .accept(PROJECT_VERSION, prop));
            projectPrevVersionChanges.stream().filter(test).forEach(prop -> into
                    .accept(PROJECT_PREV_VERSION, prop));
        }

        boolean isEmpty()
        {
            return familyVersionChanges.isEmpty()
                    && familyPrevVersionChanges.isEmpty()
                    && projectVersionChanges.isEmpty()
                    && projectPrevVersionChanges.isEmpty();
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

    private <T> Set<VersionRepresentingProperty<T>> collectPropertyChanges(
            PropertyRole role,
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
                        VersionRepresentingProperty<T> change = new VersionRepresentingProperty<>(
                                property,
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

        public VersionRepresentingProperty(String property, Pom in, T target,
                String oldValue)
        {
            this.property = property;
            this.in = in;
            this.target = target;
            this.oldValue = oldValue;
        }

        boolean matches(Pom pom)
        {
            // this is wrong
            return pom.equals(in) || (ProjectFamily.fromGroupId(pom.groupId()).equals(target))
                    || pom.coords
                    .toPlainMavenCoordinates()
                    .equals(target);
                    /*
                    && (ProjectFamily.fromGroupId(pom.groupId()).equals(target) || pom.coords
                    .toPlainMavenCoordinates()
                    .equals(target))*/
        }

        @Override
        public String toString()
        {
            return in.pom.getParent().getFileName()
                    .resolve(in.pom.getFileName()) + "\t" + property + " in "
                    + target + " for " + " currently " + oldValue;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.property);
            hash = 97 * hash + Objects.hashCode(this.in);
            hash = 97 * hash + Objects.hashCode(this.target);
            hash = 97 * hash + Objects.hashCode(this.oldValue);
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
            if (!Objects.equals(this.oldValue, other.oldValue))
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
        OTHER;

        boolean isFamily()
        {
            return this == FAMILY_PREV_VERSION || this == FAMILY_VERSION;
        }

        boolean isProject()
        {
            return this == PROJECT_PREV_VERSION || this == PROJECT_VERSION;
        }

        boolean isPrevious()
        {
            return this == PROJECT_PREV_VERSION || this == FAMILY_PREV_VERSION;
        }

        PomVersion value(VersionChange change)
        {
            if (this == OTHER)
            {
                throw new IllegalStateException("Not a version property");
            }
            if (isPrevious())
            {
                return change.oldVersion;
            }
            else
            {
                return change.newVersion;
            }
        }
    }
}
