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

import com.mastfrog.function.state.Bool;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.published.PublishChecker;
import com.telenav.cactus.scope.ProjectFamily;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static com.telenav.cactus.maven.model.PomVersion.mostCommonVersion;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.DOT;
import static com.telenav.cactus.maven.model.VersionFlavorChange.UNCHANGED;
import static com.telenav.cactus.maven.refactoring.PomRole.CONFIG;
import static com.telenav.cactus.maven.refactoring.PomRole.CONFIG_ROOT;
import static com.telenav.cactus.maven.refactoring.PropertyChange.propertyChange;
import static com.telenav.cactus.scope.ProjectFamily.familyOf;

/**
 *
 * @author timb
 */
class VersionUpdateFinder
{
    final VersionChangeUpdatesCollector changes;
    final PomCategorizer categories;
    private final VersionIndicatingProperties potentialPropertyChanges;
    private final Map<ProjectFamily, VersionChange> familyVersionChanges;
    private final Map<ProjectFamily, VersionChange> syntheticFamilyVersionChanges = new HashMap<>();
    private final Set<ProjectFamily> completedFamilies = new HashSet<>();
    private final SuperpomBumpPolicy superpomBumpPolicy;
    private final VersionMismatchPolicy versionMismatchPolicy;
    private final PublishChecker publishChecker;

    VersionUpdateFinder(
            VersionChangeUpdatesCollector changes,
            PomCategorizer categories,
            VersionIndicatingProperties potentialPropertyChanges,
            Map<ProjectFamily, VersionChange> familyVersionChanges,
            SuperpomBumpPolicy superpomBumpPolicy,
            VersionMismatchPolicy versionMismatchPolicy,
            PublishChecker publishChecker)
    {
        this.changes = changes;
        this.categories = categories;
        this.potentialPropertyChanges = potentialPropertyChanges;
        this.familyVersionChanges = familyVersionChanges;
        this.superpomBumpPolicy = superpomBumpPolicy;
        this.versionMismatchPolicy = versionMismatchPolicy;
        this.publishChecker = publishChecker;
    }

    public void go()
    {
        boolean noFamilyChanges = familyVersionChanges.isEmpty();
        Map<Pom, VersionMismatchPolicyOutcome> mismatchOutcomes = null;
        // Each round can cause new version changes to be added to the set
        // of edits we're making, so iterate until nothing makes a change.
        // In particular, if a parent pom change (likely a property edit)
        // causes a cascading parent change through an entire family, we will
        // detect that and synthesize an update to that family
        do
        {
            if (noFamilyChanges && !changes.pomVersionChanges().isEmpty())
            {
                cascadeCurrentChanges();
            }
            // We will aggregate the version mismatch outcomes (from the
            // VersionMismatchPolicy) so we can abort the build if we need to.
            Map<Pom, VersionMismatchPolicyOutcome> outcomes = findUpdates();
            if (mismatchOutcomes == null)
            {
                mismatchOutcomes = outcomes;
            }
            else
            {
                mismatchOutcomes.putAll(outcomes);
            }
        }
        while (changes.hasChanges());
        pruneDuplicateVersions();
        findSuperpomsThatNeedBumping();
        // See if we need to abort and do so
        StringBuilder abortMessage = new StringBuilder();
        mismatchOutcomes.forEach((pom, outcome) ->
        {
            // It may have been resolved in a later round
            if (versionMismatches().contains(pom))
            {
                switch (outcome)
                {
                    // The version adjusting has already happened - we just
                    // need to collect any that will cause us to abort the build
                    case ABORT:
                        if (abortMessage.length() > 0)
                        {
                            abortMessage.append(", ");
                        }
                        abortMessage.append(pom);
                }
            }
        });
        if (abortMessage.length() > 0)
        {
            throw new IllegalStateException("Encountered version mismatches "
                    + "with outcome ABORT: " + abortMessage);
        }
    }

    private void findSuperpomsThatNeedBumping()
    {
        // Bugfix:  With kivakit 1.6.2, we missed updating
        // telenav-superpom-intermediate-bom and telenav-superpom
        // because we were updating three families that did not include
        // the superpom family "telenav" - we need to be sure that we
        // capture cases where there is no choice but to publish a new
        // superpom, because otherwise the release will fail (nastily - c.f.
        // https://issues.sonatype.org/browse/OSSRH-82713
        // )
        if (publishChecker != null && superpomBumpPolicy.isBumpVersion())
        {
            Set<Pom> alsoBump = new HashSet<>();
            categories.eachPomWithRoleIn(pom ->
            {
                // 1. First see if we are already updating it; skip if so
                if (changes.hasVersionUpdateFor(pom))
                {
                    return;
                }
                Set<Pom> possibleUpdates = new HashSet<>();

                categories.childrenOf(pom).forEach(child ->
                {
                    boolean alreadyChanging = changes.hasParentUpdateFor(
                            child);
                    if (!alreadyChanging)
                    {
                        possibleUpdates.add(child);
                    }
                });

                // 2. Then see if we are updating any poms it is the ancestor of
                // and they are in the target families we're working on
                Set<Pom> allDescendants = new HashSet<>();
                for (Pom child : possibleUpdates)
                {
                    allDescendants.addAll(categories.descendantsOf(child));
                }
                boolean hasDescendantInTargetFamilies = false;
                for (Pom desc : allDescendants)
                {
                    if (familyVersionChanges.containsKey(familyOf(desc)))
                    {
                        hasDescendantInTargetFamilies = true;
                        break;
                    }
                }
                // 3. Then see if it is published and the published version differs
                boolean needRepublish = false;
                if (hasDescendantInTargetFamilies)
                {
                    try
                    {
                        needRepublish = publishChecker.check(pom).differs();
                    }
                    catch (IOException | InterruptedException
                            | URISyntaxException ex)
                    {
                        Exceptions.chuck(ex);
                    }
                }
                // 4. If so, add a bump for it and cascade
                if (needRepublish)
                {
                    PomVersion oldVersion = pom.version();
                    oldVersion.updatedWith(superpomBumpPolicy
                            .minimalMagnitudeFor(
                                    oldVersion), superpomBumpPolicy
                                    .changeFor(oldVersion))
                            .ifPresent(newVersion ->
                            {
                                VersionChange.versionChange(oldVersion,
                                        newVersion).ifPresent(versionChange ->
                                        {
                                            changes.changeParentVersion(pom,
                                                    pom,
                                                    versionChange);
                                            cascadeChange(pom, versionChange);
                                        });
                            });
                }
            }, PomRole.CONFIG, PomRole.CONFIG_ROOT);
        }
    }

    private Map<Pom, VersionMismatchPolicyOutcome> findUpdates()
    {
        collectPropertyChanges();
        applyFamilyVersionChanges();
        processVersionMismatches();
        updateSyntheticFamilyVersionChanges();
        return applyVersionMismatchPolicy();
    }

    void cascadeInitialPomChanges()
    {
        // If we have some specific pom changes we were handed that will have
        // ripples, make them ripple now so we don't clobber them with family changes
        pomVersionChanges().forEach((pom, vc) -> cascadeChange(pom, vc));
    }

    Map<ProjectFamily, VersionChange> allFamilyChanges()
    {
        Map<ProjectFamily, VersionChange> map = new HashMap<>(
                familyVersionChanges);
        map.putAll(syntheticFamilyVersionChanges);
        return map;
    }

    private boolean updateSyntheticFamilyVersionChanges()
    {
        // If we have updated all of the poms in a given family, it is 
        // effectively 
        Bool changed = Bool.create();
        for (ProjectFamily fam : categories.families())
        {
            if (familyVersionChanges.containsKey(fam) || syntheticFamilyVersionChanges
                    .containsKey(fam))
            {
                continue;
            }

            if (isFamilyEffectivelyUpdated(fam))
            {
                // Find the new project version we have been bumping to
                Set<Pom> changedPomsInFamily = changes.changedPomsInFamily(fam);
                mostCommonVersion(changedPomsInFamily)
                        .ifPresent(mostCommonVersion -> // it will be present unless collection empty
                        {
                            // Find the old project version we have
                            mostCommonVersion(categories.pomsForFamily(fam))
                                    .ifPresent(
                                            oldVersion ->
                                    {
                                        // Create a new change
                                        oldVersion.to(mostCommonVersion)
                                                .ifPresent(newChange -> // if they are not the same
                                                {
                                                    changed.set();
                                                    // Create a new effective family version change,
                                                    // and the next round will apply it
                                                    syntheticFamilyVersionChanges
                                                            .put(fam, newChange);
                                                });
                                    });
                        });
            }
        }
        return changed.getAsBoolean();
    }

    private void collectPropertyChanges()
    {
        // Iterate all of the properties we know about, and add PropertyChange
        // instances for any that represent things we need to change in poms
        // the filter allows us to change.
        categories.allPoms().forEach(pom ->
        {
            // Iterate all the properties that are in this pom:
            this.potentialPropertyChanges.collectMatches(pom,
                    (role, versionProperty) ->
            {
                // A place where we can track whether or not somthing has
                // changed, in which case, if we are modifying a pom, we
                // must also update its version, which we will do at the end.
                Bool changed = Bool.create();
                // The property either points to a property or a family - figure
                // out which, and look up the new version (if any) for it, and
                // generate a PropertyChange for that property if the filter
                // allows it.
                if (role.isProject())
                {
                    // A property like cactus.maven.plugin.version that references
                    // the version of a specific project
                    MavenCoordinates coords = (MavenCoordinates) versionProperty
                            .pointsTo();
                    // Find the project the property refers to
                    categories.pomFor(coords).ifPresent(
                            pomOfReVersionedProject ->
                    {
                        // Look up the new version of that project - we can skip
                        // looking up on the family here, as we will already have
                        // that
                        VersionChange change;
                        // If the pom has a <version> tage of its own, we need
                        // to look in our set of version changes for poms
                        if (pomOfReVersionedProject.hasExplicitVersion())
                        {
                            change = pomVersionChanges().get(
                                    pomOfReVersionedProject);
                        }
                        else
                        {
                            // Otherwise, any change we have will be a parent
                            // version change
                            //
                            // But since we might be explicitly adding a version,
                            // do look in the pom version changes first:
                            change = pomVersionChanges().get(
                                    pomOfReVersionedProject);
                            if (change == null)
                            {
                                change = parentVersionChanges().get(
                                        pomOfReVersionedProject);
                            }
                        }
                        if (change != null)
                        {
                            // Get the update version, which may be the old version
                            // from the change if the property represents the previous
                            // version of a library or family
                            PomVersion newValue = change.version(
                                    role.isPrevious());
                            // It is possible that - for whatever reason - the property
                            // may already be set to the value we want.  The optional
                            // returned by propertyChange() will be empty in that case,
                            // so we don't generate superfluous changes.
                            propertyChange(versionProperty, newValue)
                                    .ifPresent(newPropertyChange ->
                                    {
                                        // Add our change (which may have already been added),
                                        // setting the hasChanges flag in changes if the
                                        // change is new
                                        changed.set(changes.changeProperty(
                                                pom,
                                                newPropertyChange)
                                                .isChange());
                                    });
                        }
                    });
                }
                else
                {
                    // We are targeting a project family
                    ProjectFamily family = (ProjectFamily) versionProperty
                            .pointsTo();
                    // Look up the version change for the family
                    VersionChange familyVersionChange = familyVersionChanges
                            .get(family);
                    // It is possible that the version was explicitly passed
                    // to one of the public methods on this class, so in that
                    // case, prefer the version tied to the specific project,
                    // in case that is different
                    if (pomVersionChanges().containsKey(pom))
                    {
                        // If this is a configuration superpom that is getting its 
                        // version bumped independently of the family version, we do NOT
                        // want to accidentally set a property to that version instead
                        // of the right one
                        VersionChange pomChange = pomVersionChanges().get(pom);
                        if (pomChange.oldVersion().is(
                                versionProperty.oldValue()))
                        {
                            familyVersionChange = pomVersionChanges().get(pom);
                        }
                        else
                        {
//                            changes.addVersionMismatch(pom);
                            changed.set(true); // HUH?
                        }
                    }
                    if (familyVersionChange != null)
                    {
                        // Get our new version, which may be the previous version if that's
                        // what the property represents (e.g. cactus.prev.version for
                        // processing cactus libraries)
                        PomVersion newVersion = familyVersionChange.version(role
                                .isPrevious());
                        // Again, if this is not going to result in the property value
                        // being any different, then this will return empty() and we
                        // are done - the property could already be where we want to
                        // put it.
                        propertyChange(versionProperty, newVersion)
                                .ifPresent(newPropertyChange ->
                                {
                                    // Set the property, and if this actually
                                    // results in a change to our set of things
                                    // to do, set the flag so we make sure to
                                    // bump the version of the pom we are
                                    // altering a property in
                                    boolean reallyHaveAChange
                                            = changes.changeProperty(pom,
                                                    newPropertyChange)
                                                    .isChange();
                                    changed.set(reallyHaveAChange);
                                });
                    }
                }
                changed.ifTrue(() ->
                {
                    // If we have a configuration superpom, it may be one that
                    // will show up as a version mismatch because it has its own
                    // versioning scheme.  Take care of updating it now.
                    //
                    // If it has the normal family versioning scheme, then
                    // the code that applies version changes to everything
                    // in a family will take care of it.
                    if (categories.is(pom, CONFIG) || categories.is(pom,
                            CONFIG_ROOT))
                    {
                        ProjectFamily fam = familyOf(pom);
                        VersionChange vc = familyVersionChanges.get(fam);
                        if (vc != null && vc.oldVersion().equals(pom.version()))
                        {
                            // If the pom has the same current version as we expect
                            // for the whole family, just change to that version
                            if (changes.changePomVersion(pom, vc).isChange())
                            {
                                cascadeChange(pom, vc);
                            }
                        }
                        else
                        {
                            // We have a superpom with some different version.  Apply
                            // the superpomBumpPolicy
                            Consumer<PomVersion> newVersionConsumer = nue ->
                            {
                                pom.version().to(nue).ifPresent(
                                        updatedPomVersion ->
                                {
                                    if (changes.changePomVersion(pom,
                                            updatedPomVersion).isChange())
                                    {
                                        // If we're changing a parent pom's version,
                                        // walk all of its children and apply the
                                        // appropriate parent change to them
                                        cascadeChange(pom, updatedPomVersion);
                                    }
                                });
                            };
                            if (vc != null && superpomBumpPolicy.isBumpVersion())
                            {
                                pom.version().updatedWith(
                                        superpomBumpPolicy.magnitudeFor(vc),
                                        superpomBumpPolicy.changeFor(
                                                vc.newVersion()))
                                        .ifPresent(newVersionConsumer);
                            }
                            else
                            {
                                if (superpomBumpPolicy.isBumpVersion())
                                {
                                    pom.version().updatedWith(DOT, UNCHANGED)
                                            .ifPresent(newVersionConsumer);
                                }
                            }
                        }
                    }
                });
            });
        });
    }

    private void applyFamilyVersionChanges()
    {
        // Iterate all of the family version changes, and record them as
        // applying to all poms in that family unless some other version change
        // specific to that pom was already recorded.
        allFamilyChanges().forEach((family, expectedVersionChange) ->
        {
            // No need to process a family more than once - it will not
            // get any more complete than complete
            if (completedFamilies.contains(family))
            {
                return;
            }
            // Go through all the poms
            categories.eachPomInFamily(family, pom ->
            {
                // If we already have a change (perhaps from a caller adding
                // an explicit version change), then skip this pom
                if (!pomVersionChanges().containsKey(pom) && !versionMismatches()
                        .contains(pom))
                {
                    // Check if the current version is what we expect - otherwise
                    // we will need to add a mismatch to possibly resolve with
                    // the policy, or in the next round
                    boolean match = expectedVersionChange.oldVersion()
                            .isVersionOf(pom);
                    if (!match)
                    {
                        // a mismatch - the version we're changing FROM is not
                        // what we are expecting it to be
                        changes.addVersionMismatch(pom);
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
                                Optional<Pom> par = categories.parentOf(pom);
                                if (par.isPresent())
                                {
                                    // Else the version comes from its parent so we
                                    // will change that
                                    VersionChangeUpdatesCollector.ChangeResult result = changes
                                            .changeParentVersion(pom,
                                                    par.get(),
                                                    expectedVersionChange);
                                    // If the filter blocks us bumping the parent version,
                                    // we may need to add an explicit version to a pom
                                    // that didn't have one so that it gets a new version even
                                    // if we aren't allowed to touch the parent
                                    if (result.isFiltered())
                                    {
                                        changes.changePomVersion(pom,
                                                expectedVersionChange);
                                    }
                                }
                            }
                        }
                }
            });
            completedFamilies.add(family);
        });
    }

    private void processVersionMismatches()
    {
        // Now see if we have version mismatches in poms which are superpoms -
        // they have no modules and are "pom" packaging - these should just be
        // bumped, as we allow superpoms to have their own versioning schemes
        if (!versionMismatches().isEmpty() && superpomBumpPolicy.isBumpVersion())
        {
            eachVersionMismatch((pom, familyOrPomChange) ->
            {
                // We are only interested in poms that provide configuration
                // information - i.e. parents of something
                if (categories.is(pom, CONFIG)
                        || categories.is(pom, CONFIG_ROOT))
                {
                    VersionFlavorChange flavorChange = VersionFlavorChange.UNCHANGED;
                    VersionChangeMagnitude magnitudeChange = VersionChangeMagnitude.DOT;
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
                        if (familyVersionChanges.containsKey(familyOf(pom)))
                        {
                            flavorChange = superpomBumpPolicy.changeFor(
                                    familyOrPomChange.newVersion());
                            magnitudeChange = superpomBumpPolicy.magnitudeFor(
                                    familyOrPomChange);
                        }
                    }
                    // Compute our new version
                    PomVersion newVersion = pom.version().updatedWith(
                            magnitudeChange, flavorChange).get();
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
                                    Optional<Pom> parPar = categories.parentOf(
                                            pom);
                                    if (parPar.isPresent())
                                    {
                                        if (changes.changeParentVersion(par,
                                                parPar.get(), vc)
                                                .isFiltered())
                                        {
                                            // If the filter won't let us change the parent
                                            // version, we need to insert a new version tag
                                            // into the pom
                                            changes.changePomVersion(pom, vc);
                                        }
                                    }
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
    }

    private Map<Pom, VersionMismatchPolicyOutcome> applyVersionMismatchPolicy()
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
                    case COERCE_TO_TARGET_VERSION:
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

    private void cascadeChange(Pom pom, VersionChange chg)
    {
        for (Pom kid : categories.childrenOf(pom))
        {
            if (!parentVersionChanges().containsKey(kid))
            {
                if (changes.changeParentVersion(kid, pom, chg).isChange())
                {
                    cascadeChange(kid, chg);
                }
            }
        }
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
        for (Iterator<Pom> it = new HashSet<>(versionMismatches()).iterator(); it
                .hasNext();)
        {
            Pom pom = it.next();
            // Something we called may have already triggered removal
            if (!versionMismatches().contains(pom))
            {
                continue;
            }
            VersionChange change = this.pomVersionChanges().get(pom);
            if (change == null)
            {
                change = this.familyVersionChanges.get(familyOf(pom));
            }
            if (change == null || c.test(pom, change))
            {
                changes.removeVersionMismatch(pom);
            }
        }
    }

    private boolean isFamilyEffectivelyUpdated(ProjectFamily family)
    {
        Map<ProjectFamily, Set<Pom>> javaProjects = categories
                .projectsInFamilyWithRole(PomRole.JAVA);
        Map<ProjectFamily, Set<Pom>> boms = categories.projectsInFamilyWithRole(
                PomRole.BILL_OF_MATERIALS);

        Set<Pom> mix = new HashSet<>();
        if (javaProjects.containsKey(family))
        {
            mix.addAll(javaProjects.get(family));
        }
        if (boms.containsKey(family))
        {
            mix.addAll(boms.get(family));
        }
        return changes.allChangedPoms().containsAll(mix);
    }

    private Map<Pom, VersionChange> pomVersionChanges()
    {
        return changes.pomVersionChanges();
    }

    private Map<Pom, VersionChange> parentVersionChanges()
    {
        return changes.parentVersionChanges();
    }

    private Set<Pom> versionMismatches()
    {
        return changes.versionMismatches();
    }

    private void cascadeCurrentChanges()
    {
        // We need this for single superpom changes
        HashMap<Pom, VersionChange> ch = new HashMap<>(changes
                .pomVersionChanges());
        Set<Pom> seen = new HashSet<>();
        while (!ch.isEmpty())
        {
            ch.forEach((pom, ver) ->
            {
                seen.add(pom);
                cascadeChange(pom, ver);
            });

            ch.putAll(changes.pomVersionChanges());
            for (Pom p : seen)
            {
                ch.remove(p);
            }
        }
    }

    private void pruneDuplicateVersions()
    {
        new HashMap<>(changes.pomVersionChanges()).forEach((pom, verChange) ->
        {
            VersionChange parentChange = changes.parentVersionChanges().get(pom);
            if (parentChange != null && verChange.newVersion().equals(
                    parentChange.version()))
            {
                categories.parentOf(pom).ifPresent(parentPom ->
                {
                    if (!pom.hasExplicitVersion())
                    {
                        changes.removePomVersionChange(pom);
                    }
                });
            }
        });
    }
}
