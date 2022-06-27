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
import com.mastfrog.function.state.Bool;
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.MavenVersioned;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
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
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import org.w3c.dom.Document;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.DOT;
import static com.telenav.cactus.maven.model.VersionFlavorChange.UNCHANGED;
import static com.telenav.cactus.maven.xml.XMLReplacer.writeXML;
import static com.telenav.cactus.maven.refactoring.PomRole.*;

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
    private final PomCategories categories;
    private final VersionIndicatingProperties propertyChanges;
    private final Map<ProjectFamily, VersionChange> familyVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> pomVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> parentVersionChanges = new HashMap<>();
    private final Set<Pom> versionMismatches = new HashSet<>();
    private boolean bumpVersionsOfSuperpoms;
    private VersionMismatchPolicy versionMismatchPolicy
            = VersionMismatchPolicyOutcome.ABORT;
    private boolean pretend;
    private VersionUpdateFilter filter;

    public VersionReplacementFinder(Poms poms)
    {
        categories = new PomCategories(poms);
        propertyChanges = VersionIndicatingProperties.create(categories);
    }

    public VersionReplacementFinder bumpVersionsOfSuperpoms()
    {
        bumpVersionsOfSuperpoms = true;
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
        this.filter = filter;
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
                .bumpVersionsOfSuperpoms()
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
        return categories.poms().get(artifactId).map(pom ->
        {
            return withSinglePomChange(pom, newVersion);
        }).orElse(this);
    }

    public VersionReplacementFinder withSinglePomChange(
            ArtifactId artifactId, GroupId group, PomVersion newVersion)
    {
        return categories.poms().get(group, artifactId).map(pom ->
        {
            return withSinglePomChange(pom, newVersion);
        }).orElse(this);
    }

    public <P extends MavenIdentified & MavenVersioned> VersionReplacementFinder
            withSinglePomChange(P what, PomVersion newVersion)
    {
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
        for (Iterator<Pom> it = versionMismatches.iterator(); it.hasNext();)
        {
            Pom pom = it.next();
            VersionChange change = this.pomVersionChanges.get(pom);
            if (change == null)
            {
                change = this.familyVersionChanges.get(ProjectFamily
                        .fromGroupId(pom.groupId()));
            }
            if (change == null || c.test(pom, change))
            {
                it.remove();
            }
        }
    }

    private void resolveVersionMismatchesAndFinalizeUpdateSet()
    {
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
        VersionChangeUpdatesCollector changes
                = new VersionChangeUpdatesCollector(pomVersionChanges,
                        parentVersionChanges);
        // Collect any outcomes from the policy installed on this instance,
        // so we can abort if any of them say to
        Map<Pom, VersionMismatchPolicyOutcome> outcomes = new HashMap<>();
        do
        {
            resolveVersionMismatchesAndFinalizeUpdateSet(changes, outcomes);
        } // loop until no more changes
        while (changes.hasChanges() && !versionMismatches.isEmpty());

        // Collect just the fatal mismatches so we can abort if we need to
        Set<Pom> fatals = new HashSet<>();
        outcomes.forEach((pom, outcome) ->
        {
            if (outcome == VersionMismatchPolicyOutcome.ABORT)
            {
                fatals.add(pom);
            }
        });
        // Abort if necessary
        if (!fatals.isEmpty())
        {
            StringBuilder sb = new StringBuilder(
                    "Have unresolvable version mismatches:");
            for (Pom pom : fatals)
            {
                sb.append('\n').append(pom);
            }
            throw new IllegalStateException(sb.toString());
        }
    }

    private void applyFamilyVersionChanges(VersionChangeUpdatesCollector changes)
    {
        // Iterate all of the family version changes, and record them as
        // applying to all poms in that family unless some other version change
        // specific to that pom was already recorded.
        familyVersionChanges.forEach((family, expectedVersionChange) ->
        {
            categories.eachPomInFamily(family, pom ->
            {
                if (!pomVersionChanges.containsKey(pom) && !versionMismatches
                        .contains(pom))
                {
                    // Check if the current version is what we expect - otherwise
                    // we will need to add a mismatch to possibly resolve with
                    // the policy, or in the next round
                    PomVersion currentVersion = pom.version();
                    boolean match = currentVersion.equals(
                            expectedVersionChange.oldVersion());
                    if (!match)
                    {
                        versionMismatches.add(pom);
                    }
                    else
                        if (match)
                        {
                            // If it has its own <version> tag, change its version
                            if (pom.hasExplicitVersion())
                            {
                                changes.changePomVersion(pom,
                                        expectedVersionChange);
                            }
                            else
                            {
                                // Else the version comes from its parent so we
                                // will change that
                                changes.changeParentVersion(pom,
                                        expectedVersionChange);
                            }
                        }
                }
            });
        });
    }

    private Map<Pom, VersionMismatchPolicyOutcome> applyVersionMismatchPolicy(
            VersionChangeUpdatesCollector changes)
    {
        // For any remaining mismatches, apply the mismatch policy to
        // resolve them
        Map<Pom, VersionMismatchPolicyOutcome> outcomes = new HashMap<>();
        eachVersionMismatch((pom, expectedChangeOrNull) ->
        {
            if (expectedChangeOrNull != null)
            {
                VersionMismatchPolicyOutcome outcome
                        = versionMismatchPolicy.mismatchEncountered(pom,
                                expectedChangeOrNull,
                                categories.rolesFor(pom));
                outcomes.put(pom, outcome);
                switch (outcome)
                {
                    case ABORT:
                        // Leave it as a version mismatch by returning false
                        break;
                    case SKIP:
                        // Mark the mismatch resolved by returning true to
                        // remove it from the set of mismatches
                        return true;
                    case BRING_TO_TARGET_VERSION:
                        // Just record a version change to the thing it's
                        // supposed to be
                        changes.changePomVersion(pom, expectedChangeOrNull);
                        return true;
                    case BUMP:
                        // Bump the version of it from whatever it is now, to
                        // that + 1, changing the same magnitude decimal as
                        // the version change for the family, and bringing it
                        // to whatever flavor the version change for the family
                        // uses.
                        VersionFlavor newFlavor
                                = expectedChangeOrNull.newVersion().flavor();

                        Optional<PomVersion> res = pom.version().updatedWith(
                                VersionChangeMagnitude.DOT,
                                VersionFlavorChange.between(expectedChangeOrNull
                                        .oldVersion().flavor(), newFlavor));
                        if (res.isPresent())
                        {
                            changes.changePomVersion(pom, new VersionChange(pom
                                    .version(), res.get()));
                        }
                        // If res.isAbsent() then the mismatch is because the
                        // computed new version is what the pom is already at,
                        // so return true and remove it from the list of
                        // mismatches either way - it is inconsistent, but it is
                        // inconsistent because it is already at the version we
                        // are trying to bring it to.  This can happen if there
                        // was a previous version update that failed somehow.
                        return true;
                    default:
                        throw new AssertionError(outcome);
                }
            }
            return false;
        });
        return outcomes;
    }

    private void resolveVersionMismatchesAndFinalizeUpdateSet(
            VersionChangeUpdatesCollector changes,
            Map<Pom, VersionMismatchPolicyOutcome> outcomes)
    {
        // Winnows down the final set of changes we're going to make before we
        // construct our change set.
        //
        // The work done here is idempotent, so it is harmless if it is
        // called multiply (we do in toString() as well).
        //
        // First, iterate all the parent poms that have their versions changing
        // and make sure we have a parent version change recorded for their
        // children
//        ensureChildrenOfParentPomsBeingReversionedAreUpdated(changes);
//        ensureAllParentChangesHaveCorrespondingChangesForParent(changes);
        // Make sure there is a version change for every project in the families
        // we are changing versions for
        applyFamilyVersionChanges(changes);
        // Now see if we have version mismatches in poms which are superpoms - 
        // they have no modules and are "pom" packaging - these should just be
        // bumped, as we allow superpoms to have their own versioning schemes
        if (!versionMismatches.isEmpty() && bumpVersionsOfSuperpoms)
        {
            eachVersionMismatch((pom, familyOrPomChange) ->
            {
                // We are only interested in poms that provide configuration
                // information - i.e. parents of something
                if (categories.is(pom, CONFIG)
                        || categories.is(pom, CONFIG_ROOT))
                {
                    VersionFlavorChange flavorChange = VersionFlavorChange.UNCHANGED;
                    VersionChangeMagnitude bumpMagnitude = VersionChangeMagnitude.DOT;
                    // If we have a family change, compute a new version based on
                    // the kind of change (magnitude, flavor) it is making.
                    if (familyOrPomChange != null)
                    {
                        // Check that it is really caused by a family change.
                        // We will make requested changes consistent with the
                        // version request for that family;  side-effect
                        // changes (a parent of some family not directly being
                        // modified changed, so we have to cascade parent pom
                        // version updates up through all of its children)
                        // should not mess with the suffix
                        if (familyVersionChanges.containsKey(ProjectFamily
                                .fromGroupId(pom.groupId())))
                        {
                            flavorChange = familyOrPomChange.newVersion()
                                    .flavor()
                                    .toThis();
                            bumpMagnitude = familyOrPomChange.magnitudeChange();
                        }
                    }
                    // Compute our new version
                    PomVersion newVersion = pom.version().updatedWith(
                            bumpMagnitude, flavorChange).get();
                    VersionChange vc = new VersionChange(pom.version(),
                            newVersion);
                    if (!pom.hasExplicitVersion())
                    {
                        // If the pom does not have its own <version> tag, then
                        // we either need to add a version tag to it overriding
                        // that from its parent (if the parent will not be changed)
                        // or we need to update its parent's version (in which case
                        // we will pick up that this pom needs updating for the new
                        // parent version on the next round).
                        List<Pom> parents = categories.parents(pom);
                        for (Pom par : parents)
                        {
                            if (par.version().equals(pom.version()))
                            {
                                if (par.hasExplicitVersion())
                                {
                                    changes.changePomVersion(par, vc);
                                }
                                else
                                {
                                    changes.changeParentVersion(par, vc);
                                }
                                return true;
                            }
                        }
                    }
                    else
                    {
                        changes.changePomVersion(pom, vc);
                        return true;
                    }
                    return true;
                }
                return false;
            });
        }
        // We may have made changes that can be consumed by these:
//        ensureAllParentChangesHaveCorrespondingChangesForParent(changes);
        ensureChildrenOfParentPomsBeingReversionedAreUpdated(changes);
        ensurePomsWithPropertyChangesGetTheirVersionUpdated(changes);
        removeDirectVersionChangesForPomsThatUseParentVersion(changes);
        applyVersionMismatchPolicy(changes);
    }

    private VersionChange versionChangeFor(Pom pom)
    {
        VersionChange vc = pomVersionChanges.get(pom);
        if (vc == null && !pom.hasExplicitVersion())
        {
            vc = parentVersionChanges.get(pom);
        }
        return vc;
    }

    private void removeDirectVersionChangesForPomsThatUseParentVersion(
            VersionChangeUpdatesCollector changes)
    {
        // We may have generated pom version change where the version change
        // actually pertains to the parent - move it there if so.
        Map<Pom, VersionChange> directChanges = new HashMap<>(pomVersionChanges);
        directChanges.forEach((pom, versionChange) ->
        {
            if (!pom.hasExplicitVersion())
            {
                Optional<Pom> par = categories.parentOf(pom);
                if (par.isPresent())
                {
                    VersionChange vc = versionChangeFor(par.get());
                    if (vc != null)
                    {
                        changes.changeParentVersion(pom, vc);
                    }
                }
                changes.removePomVersionChange(pom);
            }
        });
    }

    private boolean hasPropertyChange(Pom pom)
    {
        Bool any = Bool.create();
        propertyChanges.collectMatches(pom, (kind, prop) ->
        {
            any.set();
        });
        return any.getAsBoolean();
    }

    private void ensureAllParentChangesHaveCorrespondingChangesForParent(
            VersionChangeUpdatesCollector changes)
    {
        // Need a copy in case of modification
        new HashMap<>(parentVersionChanges).forEach((pom, ver) ->
        {
            categories.parentOf(pom).ifPresent(parentPom ->
            {
                if (parentPom.hasExplicitVersion())
                {
                    System.out.println("AD PVC " + parentPom.coords + " " + ver);
                    changes.changePomVersion(parentPom, ver);
                }
                else
                {
                    changes.changeParentVersion(parentPom, ver);
                }
            });;
        });
    }

    private void ensurePomsWithPropertyChangesGetTheirVersionUpdated(
            VersionChangeUpdatesCollector changes)
    {
        // Make sure any pom we are making a change in is getting a version change - 
        // along with its children - so if we are changing cactus.version in
        // telenav-superpom, we need a new version of all superpoms descending
        // from it, and all projects that use that as the parent will also
        // need updating (in the next round those will be applied).
        categories.eachPom(pom ->
        {
            if (categories.is(pom, PARENT) || categories.is(pom, CONFIG) || categories
                    .is(pom, CONFIG_ROOT))
            {
                PomVersion currVersion = pom.version();
                VersionChange ch = versionChangeFor(pom);
                if (ch == null)
                {
                    ProjectFamily fam = ProjectFamily.fromGroupId(pom.groupId());
                    ch = familyVersionChanges.get(fam);
                }
                if (ch != null)
                {
                    if (currVersion.equals(ch.newVersion()))
                    {
                        return;
                    }
                    if (currVersion.equals(ch.oldVersion()))
                    {
                        if (pom.hasExplicitVersion())
                        {
                            changes.changePomVersion(pom, ch);
                        }
                        else
                        {
                            changes.changeParentVersion(pom, ch);
                        }
                    }
                    else
                        if (pom.hasExplicitVersion())
                        {
                            boolean isExplicit = familyVersionChanges
                                    .containsKey(ProjectFamily.fromGroupId(
                                            pom.groupId()));
                            VersionFlavorChange flavorChange = VersionFlavorChange.UNCHANGED;
                            if (isExplicit)
                            {
                                flavorChange = familyVersionChanges.get(
                                        ProjectFamily.fromGroupId(
                                                pom.groupId())).newVersion()
                                        .flavor().toThis();
                            }
                            // Superpom of something else that contains a property
                            // we are updating, so we need to update its verison,
                            // which will cause us to need to update everything
                            // that uses it as a parent and shares its version
                            PomVersion newVersion = currVersion.updatedWith(
                                    ch.magnitudeChange().notNone(),
                                    flavorChange).get();
                            VersionChange newChange = new VersionChange(
                                    currVersion, newVersion);
                            changes.changePomVersion(pom, newChange);
                        }
                }
                else
                {
                    if (hasPropertyChange(pom))
                    {
                        PomVersion newVersion = currVersion.updatedWith(
                                DOT, UNCHANGED).get();
                        VersionChange newChange = new VersionChange(
                                currVersion, newVersion);
                        if (pom.hasExplicitVersion())
                        {
                            changes.changePomVersion(pom, newChange);
                        }
                    }
                }
            }
        });
    }

    private void ensureChildrenOfParentPomsBeingReversionedAreUpdated(
            VersionChangeUpdatesCollector changes)
    {
        // Ensure parent pom children have updates pending
        categories.eachPom(pom -> categories.parentOf(pom).ifPresent(parent ->
        {
            VersionChange vc = pomVersionChanges.get(parent);
            if (vc == null && !parent.hasExplicitVersion())
            {
                vc = parentVersionChanges.get(parent);
            }
            if (vc != null)
            {
                changes.changeParentVersion(pom, vc);
            }
        }));
    }

    private boolean filterAccepts(VersionProperty<?> prop, PomVersion change)
    {
        if (filter == VersionUpdateFilter.DEFAULT)
        {
            return true;
        }
        return filter
                .shouldUpdateVersionProperty(prop.in, prop.property, change,
                        prop.oldValue);
    }

    private Set<AbstractXMLUpdater> replacers()
    {
        resolveVersionMismatchesAndFinalizeUpdateSet();
        Set<AbstractXMLUpdater> replacers = new LinkedHashSet<>();

        categories.eachPom(pom ->
        {
            VersionChange vc = this.pomVersionChanges.get(pom);
            Set<VersionProperty<?>> matched = new HashSet<>();
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
                }
                else
                {
                    MavenCoordinates coords = (MavenCoordinates) prop.target;
                    Pom target = categories.poms().get(coords).get();
                    assert coords.equals(target.coords);
                    VersionChange cd = this.pomVersionChanges.get(target);
                    if (cd != null)
                    {
                        newValue = kind.value(cd).text();
                    }
                }
                if (newValue != null)
                {
                    if (filterAccepts(prop, PomVersion.of(newValue)))
                    {

                        String query = "/project/properties/" + prop.property;
                        replacers.add(
                                new XMLTextContentReplacement(PomFile.of(pom),
                                        query,
                                        newValue));
                    }
                }
            });
            if (vc != null)
            {
                if (filter.shouldUpdatePomVersion(pom, vc))
                {

                    if (pom.hasExplicitVersion())
                    {
                        String query = "/project/version";
                        replacers.add(new XMLTextContentReplacement(
                                PomFile.of(pom),
                                query,
                                vc.newVersion().text()));
                    }
                    else
                    {
                        replacers.add(new XMLVersionElementAdder(
                                PomFile.of(pom),
                                vc.newVersion().text()));
                    }
                }
            }
            VersionChange parentChange = parentVersionChanges.get(pom);
            if (parentChange != null)
            {
                Pom parentPom = categories.parentOf(pom).get();
                if (filter.shouldUpdateParentVersion(pom, parentPom,
                        parentChange))
                {

                    String query = "/project/parent/version";
                    replacers.add(new XMLTextContentReplacement(PomFile.of(pom),
                            query,
                            parentChange.newVersion().text()));
                }
            }
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
        List<AbstractXMLUpdater> replacers = new ArrayList<>(replacers());
        // Ensure a consistent order for the sanity of anyone reading a log
        // repeatedly.
        Collections.sort(replacers);
        try
        {
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

    @Override
    public String toString()
    {
        Set<AbstractXMLUpdater> replacers = replacers();
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
