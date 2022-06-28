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
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.MavenVersioned;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLElementRemoval;
import com.telenav.cactus.maven.xml.XMLVersionElementAdder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import org.w3c.dom.Document;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.xml.XMLReplacer.writeXML;
import static com.telenav.cactus.scope.ProjectFamily.familyOf;

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
    private final PomCategorizer categories;
    private final VersionIndicatingProperties potentialPropertyChanges;
    private final Map<ProjectFamily, VersionChange> familyVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> pomVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> parentVersionChanges = new HashMap<>();
    private final Map<Pom, Set<PropertyChange<?, PomVersion>>> propertyChanges = new HashMap<>();
    private final Set<Pom> removeExplicitVersionFrom = new HashSet<>();
    private final Set<Pom> versionMismatches = new HashSet<>();
    private SuperpomBumpPolicy superpomBumpPolicy = SuperpomBumpPolicy.BUMP_WITHOUT_CHANGING_FLAVOR;
    private VersionMismatchPolicy versionMismatchPolicy
            = VersionMismatchPolicyOutcome.ABORT;
    private boolean pretend;
    private VersionUpdateFilter filter;
    private boolean needResolve = true;

    public VersionReplacementFinder(Poms poms)
    {
        categories = new PomCategorizer(poms);
        potentialPropertyChanges = VersionIndicatingProperties
                .create(categories);
    }

    public VersionReplacementFinder withSuperpomBumpPolicy(
            SuperpomBumpPolicy policy)
    {
        this.superpomBumpPolicy = notNull("policy", policy);
        return this;
    }

    public VersionReplacementFinder withVersionMismatchPolicy(
            VersionMismatchPolicy policy)
    {
        this.versionMismatchPolicy = notNull("policy", policy);
        return this;
    }

    public VersionReplacementFinder pretend(boolean pretendMode)
    {
        this.pretend = pretendMode;
        return this;
    }

    public VersionReplacementFinder withFilter(VersionUpdateFilter filter)
    {
        if (!needResolve)
        {
            throw new IllegalStateException("Cannot set filter at this point");
        }
        this.filter = filter;
        needResolve = true;
        return this;
    }

    public static void main(String[] args) throws Exception
    {
//        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff"));
//        Poms poms = Poms.in(Paths.get("/Users/timb/work/personal/mastfrog-parent"));
        Poms poms = Poms.in(Paths.get(
                "/Users/timb/work/personal/http-test-harness"));
//        Poms poms = Poms.in(Paths.get("/tmp/jonstuff"));
        VersionReplacementFinder vr = new VersionReplacementFinder(poms)
                .withSuperpomBumpPolicy(
                        SuperpomBumpPolicy.BUMP_ACQUIRING_NEW_FAMILY_FLAVOR)
                .withVersionMismatchPolicy(VersionMismatchPolicyOutcome.SKIP)
                .withSinglePomChange(ArtifactId.of("http-test-harness-parent"),
                        PomVersion.of("0.9.6-dev")) //                .withFamilyVersionChange(ProjectFamily.named("mastfrog"),
                //                        PomVersion.of("2.0.102"), PomVersion.of("2.0.103")) 
                //                .withSinglePomChange(
                //                        ArtifactId.of("telenav-build"),
                //                        PomVersion.of("2.1.1"))
                //                .withFamilyVersionChange(ProjectFamily.named("cactus"),
                //                        PomVersion.of("1.4.12"),
                //                        PomVersion.of("1.5.0-SNAPSHOT"))
                //                .withFamilyVersionChange(ProjectFamily.named("kivakit"),
                //                        PomVersion.of("1.6.0"),
                //                        PomVersion.of("1.6.2-SNAPSHOT"))
                //                .withFamilyVersionChange(ProjectFamily.named("lexakai"),
                //                        PomVersion.of("1.0.8"),
                //                        PomVersion.of("1.0.9"))
                //x
                ;
        System.out.println(vr);
        vr.go();
    }

    public VersionReplacementFinder withSinglePomChange(
            ArtifactId artifactId, PomVersion newVersion)
    {
        needResolve = true;
        return categories.poms().get(artifactId).map(pom ->
        {
            return withSinglePomChange(pom, newVersion);
        }).orElse(this);
    }

    public Optional<VersionChange> versionChangeFor(Pom pom)
    {
        return Optional.ofNullable(pomVersionChanges.get(pom));
    }

    public VersionReplacementFinder withSinglePomChange(
            ArtifactId artifactId, GroupId group, PomVersion newVersion)
    {
        needResolve = true;
        return categories.poms().get(group, artifactId).map(pom ->
        {
            return withSinglePomChange(pom, newVersion);
        }).orElse(this);
    }

    public <P extends MavenIdentified & MavenVersioned> VersionReplacementFinder
            withSinglePomChange(P what, PomVersion newVersion)
    {
        needResolve = true;
        Consumer<Pom> c = pom ->
        {
            if (!pom.version().equals(newVersion))
            {
                pomVersionChanges.put(pom, new VersionChange(pom.version(),
                        newVersion));
            }
        };
        if (what instanceof Pom)
        {
            c.accept((Pom) what);
        }
        else
        {
            categories.poms().get(what).toOptional().ifPresent(c);
        }
        return this;
    }

    public VersionReplacementFinder withFamilyVersionChange(ProjectFamily family,
            PomVersion old, PomVersion nue)
    {
        needResolve = true;
        VersionChange vc = new VersionChange(old, nue);
        familyVersionChanges.put(family, vc);
        return this;
    }

    /**
     * Iterate all the version mismatches, passing each one's pom and the
     * version (may be null) we are trying to change it to.
     *
     * @param c A biconsumer
     */
    private void eachVersionMismatch(BiPredicate<Pom, VersionChange> c)
    {
        // Need a defensive copy because we may not just remove from
        // but add to the collection while iterating
        for (Iterator<Pom> it = new HashSet<>(versionMismatches).iterator(); it
                .hasNext();)
        {
            Pom pom = it.next();
            // Something we called may have already triggered removal
            if (!versionMismatches.contains(pom))
            {
                continue;
            }
            VersionChange change = this.pomVersionChanges.get(pom);
            if (change == null)
            {
                change = this.familyVersionChanges.get(familyOf(pom));
            }
            if (change == null || c.test(pom, change))
            {
                versionMismatches.remove(pom);
            }
        }
    }

    /**
     * Main entry point for computing version changes.
     */
    private void resolveVersionMismatchesAndFinalizeUpdateSet()
    {
        if (!needResolve)
        {
            // We already ran, no need to do it again as some of it is
            // expensive.  The work is idempotent, but pointless to do
            // twice (toString() also calls this method to make sure the
            // description of what we're going to do is accurate).
            return;
        }

        // The VersionChangeUpdatesCollector we pass changes to, and it
        // records whether or not there was an actual change to the stored
        // values that represent what we're going to do.
        //
        // Its hasChanges() resets the changed state.
        //
        // We need to run this iteratively until no new changes have been
        // added, because each round may add changes to additional poms
        // which have children, so those children get the fact that their
        // parent version needs updating recorded in the next round, and
        // so forth, until no change has been made
        new VersionUpdateFinder(changeCollector(), categories,
                potentialPropertyChanges,
                familyVersionChanges, superpomBumpPolicy, versionMismatchPolicy)
                .go();
        needResolve = false;
    }

    private VersionChangeUpdatesCollector changeCollector()
    {
        return new VersionChangeUpdatesCollector(pomVersionChanges,
                parentVersionChanges, propertyChanges,
                removeExplicitVersionFrom,
                versionMismatches, filter);

    }

    private Set<AbstractXMLUpdater> xmlUpdaters()
    {
        resolveVersionMismatchesAndFinalizeUpdateSet();
        Set<AbstractXMLUpdater> replacers = new LinkedHashSet<>();

        // Create <version> tag removers for poms where the value is now
        // superfluous
        removeExplicitVersionFrom.forEach(removeVersionFrom ->
        {
            replacers.add(new XMLElementRemoval(PomFile.of(removeVersionFrom),
                    "/project/version"));
        });

        // Add our property changes
        this.propertyChanges.forEach((pom, changes) ->
        {
            changes.forEach(change ->
            {
                replacers.add(new XMLTextContentReplacement(PomFile.of(pom),
                        "/project/properties/" + change.propertyName(),
                        change.newValue().text()));
            });
        });

        // Add out pom version tag changes
        this.pomVersionChanges.forEach((pom, versionChange) ->
        {
            if (pom.hasExplicitVersion())
            {
                String query = "/project/version";
                replacers.add(new XMLTextContentReplacement(
                        PomFile.of(pom),
                        query,
                        versionChange.newVersion().text()));
            }
            else
            {
                replacers.add(new XMLVersionElementAdder(PomFile.of(pom),
                        versionChange.newVersion().text()));
            }
        });
        // Add our parent version changes
        this.parentVersionChanges.forEach((pom, versionChange) ->
        {
            String query = "/project/parent/version";
            replacers.add(new XMLTextContentReplacement(
                    PomFile.of(pom),
                    query,
                    versionChange.newVersion().text()));
        });
        return replacers;
    }

    /**
     * Rewrite pom files.
     *
     * @return
     * @throws Exception
     */
    public Set<Path> go() throws Exception
    {
        return go(System.out::println);
    }

    public Set<Path> go(Consumer<String> msgs) throws Exception
    {
        List<AbstractXMLUpdater> replacers = new ArrayList<>(xmlUpdaters());
        // Ensure a consistent order for the sanity of anyone reading a log
        // repeatedly.
        Collections.sort(replacers);
        try
        {
            // Preload Document instances for all of the poms, so each document
            // change operates against any earlier changes
            return AbstractXMLUpdater.openAll(replacers, () ->
            {
                Set<Path> result = new HashSet<>();
                Map<Path, Document> docForPath = new HashMap<>();
                for (AbstractXMLUpdater rep : replacers)
                {
                    Document changed = rep.replace();
                    if (changed != null)
                    {
                        Document old = docForPath.get(rep.path());
                        if (old != changed && old != null)
                        {
                            throw new IllegalStateException(
                                    "Context did not hold - " + old + " vs "
                                    + changed + " for " + rep.path());
                        }
                        msgs.accept(" CHANGE: " + rep);
                        docForPath.put(rep.path(), changed);
                    }
                }
                for (Map.Entry<Path, Document> e : docForPath.entrySet())
                {
                    msgs.accept("Rewrite " + e.getKey() + (pretend
                                                           ? " (pretend)"
                                                           : ""));
                    if (!pretend)
                    {
                        writeXML(e.getValue(), e.getKey());
                    }
                    result.add(e.getKey());
                }
                return result;
            });
        }
        finally
        {
            // Dump our cached values
            categories.poms().reload();
        }
    }

    public int changeCount()
    {
        return changeCollector().allChangedPoms().size();
    }

    /**
     * Construct changes for a commit message, calling the consumer once per
     * change.
     *
     * @param c A function that can be passed a section heading and be returned
     * a consumer for that section
     */
    public void collectChanges(
            Function<? super String, Consumer<? super Object>> c)
    {
        if (!pomVersionChanges.isEmpty())
        {
            Consumer<? super Object> versionChanges = c.apply("Version Changes");
            // Use treemap for consistent sort
            new TreeMap<>(pomVersionChanges).forEach((pom, vc) ->
            {
                versionChanges.accept(pom.toArtifactIdentifiers() + ": " + vc);
            });
        }
        if (parentVersionChanges.isEmpty())
        {
            Consumer<? super Object> parentVersionChangeC = c
                    .apply("Parent Version Changes");
            new TreeMap<>(parentVersionChanges).forEach((pom, vc) ->
            {
                parentVersionChangeC.accept(
                        pom.toArtifactIdentifiers() + ": " + vc);
            });
        }
        if (!propertyChanges.isEmpty())
        {
            Consumer<? super Object> propertyChangeC = c.apply(
                    "Property Changes");
            new TreeMap<>(propertyChanges).forEach((pom, changes) ->
            {
                propertyChangeC.accept(
                        pom.toArtifactIdentifiers() + ": " + changes);
            });
        }
    }

    @Override
    public String toString()
    {
        Set<AbstractXMLUpdater> replacers = xmlUpdaters();
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
                sb.append(" * ").append(pom.coordinates()
                        .toArtifactIdentifiers())
                        .append(" -> ").append(ver).append('\n');
            });
        }

        if (!parentVersionChanges.isEmpty())
        {
            sb.append("PARENT CHANGES:\n");
            parentVersionChanges.forEach((pom, ver) ->
            {
                sb.append(" * ").append(pom.coordinates()
                        .toArtifactIdentifiers())
                        .append(" ->-> ").append(ver).append('\n');
            });
        }

        if (!potentialPropertyChanges.isEmpty())
        {
            sb.append("PROPERTIES REPRESENTING VERSIONS:\n");
            sb.append(potentialPropertyChanges);
        }

        if (!propertyChanges.isEmpty())
        {
            sb.append("\nPROPERTY CHANGES:\n");
            propertyChanges.forEach((pom, change) ->
            {
                sb.append(" * ").append(change).append('\n');
            });
        }
        if (!removeExplicitVersionFrom.isEmpty())
        {
            sb.append("\nREMOVE SUPERFLUOUS VERSIONS FROM:\n");
            removeExplicitVersionFrom.forEach(pom ->
            {
                sb.append(" * ")
                        .append(pom.toArtifactIdentifiers())
                        .append(' ')
                        .append(pom.path())
                        .append('\n');
            });
        }

        if (!categories.rolesForPom().isEmpty())
        {
            sb.append("ROLES:\n");
            categories.eachPomAndItsRoles((Pom pom, Set<PomRole> roles) ->
            {
                String par = categories.parentOf(pom)
                        .map(p -> p.artifactId()
                        .toString())
                        .orElse("");

                sb.append(" * ").append(pom.toArtifactIdentifiers()).append(' ')
                        .append(roles)
                        .append(versionMismatches.contains(pom)
                                ? " **VERSION-MISMATCH** " + pom.version()
                                : "")
                        .append(" parent ").append(par)
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
}
