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
package com.telenav.cactus.maven.tree;

import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.refactoring.PomCategorizer;
import com.telenav.cactus.maven.refactoring.PomRole;
import com.telenav.cactus.maven.tree.Problem.Severity;
import com.telenav.cactus.scope.ProjectFamily;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.refactoring.PomRole.*;
import static com.telenav.cactus.scope.ProjectFamily.familyOf;
import static java.util.stream.Collectors.toCollection;

/**
 *
 * @author Tim Boudreau
 */
public final class ConsistencyChecker2
{
    private boolean checkRemoteModifications;
    private boolean checkFamilies;
    private boolean checkRoles;
    private boolean checkVersions;
    private boolean checkDetached;
    private boolean checkBranches;
    private boolean checkRelativePaths;
    private boolean checkLocalModifications;
    private VersionFlavor versionFlavor;
    private final Set<ProjectFamily> familiesToCheck = new HashSet<>();
    private Predicate<GitCheckout> checkoutsFilter = _ignored -> true;
    private Consumer<String> activityLogger = System.out::println;
    private final Set<ProjectFamily> tolerateVersionInconsistenciesIn = new HashSet<>();
    private String targetBranch;

    public ConsistencyChecker2 allChecks()
    {
        checkFamilies
                = checkRelativePaths
                = checkVersions
                = checkDetached
                = checkBranches
                = checkRelativePaths
                = checkLocalModifications
                = checkRemoteModifications
                = true;
        return this;
    }

    public ConsistencyChecker2 tolerateVersionInconsistenciesIn(
            ProjectFamily fam)
    {
        this.tolerateVersionInconsistenciesIn.add(fam);
        return this;
    }

    public ConsistencyChecker2 tolerateVersionInconsistenciesIn(
            Collection<? extends ProjectFamily> c)
    {
        this.tolerateVersionInconsistenciesIn.addAll(c);
        return this;
    }

    public ConsistencyChecker2 enforceVersionFlavor(VersionFlavor versionFlavor)
    {
        this.versionFlavor = versionFlavor;
        return this;
    }

    public ConsistencyChecker2 quiet()
    {
        activityLogger = s ->
        {
        };
        return this;
    }

    public ConsistencyChecker2 activityLogger(Consumer<String> log)
    {
        this.activityLogger = log;
        return this;
    }

    public ConsistencyChecker2 checkingRepositories(
            Collection<? extends GitCheckout> all)
    {
        Set<GitCheckout> ch = new HashSet<>(all);
        checkoutsFilter = checkoutsFilter.and(ch::contains);
        return this;
    }

    public ConsistencyChecker2 checkingRepositories(Predicate<GitCheckout> test)
    {
        checkoutsFilter = checkoutsFilter.and(notNull("test", test));
        return this;
    }

    public ConsistencyChecker2 checkFamily(ProjectFamily family)
    {
        familiesToCheck.add(notNull("family", family));
        return this;
    }

    public ConsistencyChecker2 checkFamilies(
            Iterable<? extends ProjectFamily> it)
    {
        notNull("it", it).forEach(this::checkFamily);
        return this;
    }

    public ConsistencyChecker2 checkRemoteModifications()
    {
        checkRemoteModifications = true;
        return this;
    }

    public ConsistencyChecker2 checkLocalModifications()
    {
        checkLocalModifications = true;
        return this;
    }

    public ConsistencyChecker2 checkFamilies()
    {
        checkFamilies = true;
        return this;
    }

    public ConsistencyChecker2 checkRoles()
    {
        checkRoles = true;
        return this;
    }

    public ConsistencyChecker2 checkVersions()
    {
        checkVersions = true;
        return this;
    }

    public ConsistencyChecker2 checkDetached()
    {
        checkDetached = true;
        return this;
    }

    public ConsistencyChecker2 checkBranches()
    {
        checkBranches = true;
        return this;
    }

    public ConsistencyChecker2 checkRelativePaths()
    {
        checkRelativePaths = true;
        return this;
    }

    public Problems check(ProjectTree tree)
    {
        return new Checker(checkFamilies, checkRoles, checkVersions,
                checkDetached, checkBranches, checkRelativePaths,
                checkLocalModifications, checkRemoteModifications,
                familiesToCheck,
                checkoutsFilter, tree, activityLogger, targetBranch,
                tolerateVersionInconsistenciesIn, versionFlavor).check();
    }

    private Predicate<? super Pom> familyFilter()
    {
        Predicate<Pom> familyFilter;
        if (familiesToCheck.isEmpty())
        {
            familyFilter = _ignored -> true;
        }
        else
        {
            Set<ProjectFamily> defensiveCopy = new HashSet<>(familiesToCheck);
            familyFilter = pom ->
            {
                ProjectFamily fam = familyOf(pom);
                boolean result = defensiveCopy.contains(fam);
                if (!result)
                {
                    for (ProjectFamily f : defensiveCopy)
                    {

                    }
                }
                return result;
            };
        }
        return familyFilter;
    }

    public ConsistencyChecker2 withTargetBranch(String targetBranch)
    {
        this.targetBranch = targetBranch;
        return this;
    }

    private static final class Checker
    {
        private final boolean checkFamilies;
        private final boolean checkRoles;
        private final boolean checkVersions;
        private final boolean checkDetached;
        private final boolean checkBranches;
        private final boolean checkRelativePaths;
        private final boolean checkLocalModifications;
        private final boolean checkRemoteModifications;
        private final Predicate<? super ProjectFamily> familiesToCheck;
        private final Predicate<? super GitCheckout> reposFilter;
        private final ProjectTree tree;
        private final Set<ProjectFamily> tolerateVersionInconsistenciesIn;
        private Poms poms;
        private PomCategorizer categorizer;
        private Map<ProjectFamily, PomVersion> versionsForFamilies;
        private final Consumer<String> log;
        private Set<GitCheckout> checkoutsContainingSuperpoms;
        private final String targetBranch;
        private final Set<ProjectFamily> includeFamilies;
        private final VersionFlavor versionFlavor;

        public Checker(boolean checkFamilies, boolean checkRoles,
                boolean checkVersions, boolean checkDetached,
                boolean checkBranches, boolean checkRelativePaths,
                boolean checkLocalModifications,
                boolean checkRemoteModifications,
                Set<ProjectFamily> includeFamilies,
                Predicate<? super GitCheckout> reposFilter,
                ProjectTree tree,
                Consumer<String> log,
                String targetBranch,
                Set<ProjectFamily> tolerateVersionInconsistenciesIn,
                VersionFlavor versionFlavor)
        {
            this.checkFamilies = checkFamilies;
            this.checkRoles = checkRoles;
            this.checkVersions = checkVersions;
            this.checkDetached = checkDetached;
            this.checkBranches = checkBranches;
            this.checkRelativePaths = checkRelativePaths;
            this.checkLocalModifications = checkLocalModifications;
            this.includeFamilies = new HashSet<>(includeFamilies);

            this.reposFilter = reposFilter;
            this.checkRemoteModifications = checkRemoteModifications;
            this.tree = tree;
            this.log = log;
            this.targetBranch = targetBranch;
            this.tolerateVersionInconsistenciesIn = new HashSet<>(
                    tolerateVersionInconsistenciesIn);
            this.familiesToCheck = filter();
            this.versionFlavor = versionFlavor;
        }

        private Predicate<ProjectFamily> filter()
        {
            if (includeFamilies.isEmpty())
            {
                return _ignored -> true;
            }
            // To do this effectively, we need to capture pom files that
            // belong to a parent family of the targets - otherwise, we lose
            // the information _that_ some checkouts contain superpoms.
            return new Predicate<ProjectFamily>()
            {
                private final Set<ProjectFamily> parentFamilies = new HashSet<>();

                synchronized Set<ProjectFamily> parentFamilies()
                {
                    if (parentFamilies.isEmpty())
                    {
                        tree.allProjects().forEach(pom ->
                        {
                            if (includeFamilies.contains(familyOf(pom)))
                            {
                                pom.groupId().parentGroupId().ifPresent(par ->
                                {
                                    parentFamilies.add(familyOf(par));
                                });
                            }
                        });
                    }
                    return parentFamilies;
                }

                @Override
                public boolean test(ProjectFamily t)
                {
                    if (includeFamilies.contains(t))
                    {
                        return true;
                    }
                    return parentFamilies().contains(t);
                }
            };
        }

        private void log(String what)
        {
            log.accept(what);
        }

        public Problems check()
        {
            Problems result = new Problems();
            if (checkRoles)
            {
                log("Checking roles");
                checkRoles(result);
            }
            if (checkVersions)
            {
                log("Checking versions");
                checkVersions(result);
            }
            if (checkRelativePaths)
            {
                log("Checking relative paths");
                ParentRelativePathChecker checker = new ParentRelativePathChecker();
                for (Pom pom : poms().poms())
                {
                    checker.check(pom).ifPresent(result::add);
                }
            }
            if (checkFamilies)
            {
                log("Checking families within checkouts");
                checkFamilies(result);
            }
            if (checkLocalModifications)
            {
                log("Checking local modifications");
                checkLocalModifications(result);
            }
            if (checkDetached)
            {
                log("Checking detached head");
                checkDetachedHead(result);
            }
            if (checkBranches)
            {
                log("Checking branch consistency");
                checkBranches(result);
            }
            if (checkRemoteModifications)
            {
                log("Checking for un-pulled remote changes");
                checkRemoteModifications(result);
            }
            if (versionFlavor != null)
            {
                log("Checking that version flavor is " + versionFlavor);
                checkVersionFlavor(result);
            }
            return result;
        }

        private Set<GitCheckout> checkoutsContainingSuperpoms()
        {
            if (checkoutsContainingSuperpoms != null)
            {
                return checkoutsContainingSuperpoms;
            }
            PomCategorizer fullCat = new PomCategorizer(new Poms(tree
                    .allProjects()));
            checkoutsContainingSuperpoms = new HashSet<>();
            // For this, we want the unfiltered full set of poms,
            // not our winnowed group
            for (GitCheckout checkout : tree.allCheckouts())
            {
                {
                    for (Pom p : tree.projectsWithin(checkout))
                    {
                        if (fullCat.is(p, CONFIG_ROOT))
                        {
                            checkoutsContainingSuperpoms.add(checkout);
                            break;
                        }
                    }
                }
            }
            return checkoutsContainingSuperpoms;
        }

        private void checkRemoteModifications(Problems into)
        {
            Set<GitCheckout> checkouts = new TreeSet<>();
            for (Pom pom : poms.poms())
            {
                GitCheckout co = tree.checkoutFor(pom);
                if (co != null)
                {
                    checkouts.add(co);
                }
            }
            for (GitCheckout co : checkouts)
            {
                co.updateRemoteHeads();
                if (co.needsPull())
                {
                    into.add("Remote changes exist for " + co.loggingName()
                            + " which have not been pulled into "
                            + co.checkoutRoot());
                }
            }
        }

        private void checkBranches(Problems into)
        {
            Set<GitCheckout> seen = new HashSet<>();

            Set<GitCheckout> containingSuperpoms = checkoutsContainingSuperpoms();

            Map<ProjectFamily, Set<Branch>> branchForCheckout = new HashMap<>();

            Map<Branch, Set<GitCheckout>> checkoutsForBranchName = new HashMap<>();

            for (Pom pom : poms().poms())
            {
                GitCheckout co = tree.checkoutFor(pom);
                if (!containingSuperpoms.contains(co) && seen.add(co))
                {
                    tree.branches(co).currentBranch().ifPresent(branch ->
                    {
                        ProjectFamily fam = familyOf(pom);
                        Set<Branch> all = branchForCheckout.computeIfAbsent(fam,
                                f -> new HashSet<>());
                        all.add(branch);
                        Set<GitCheckout> set = checkoutsForBranchName
                                .computeIfAbsent(branch, b -> new TreeSet<>());
                        set.add(co);
                    });
                }
            }

            branchForCheckout.forEach((family, branches) ->
            {
                log("Branches for family " + family + ": " + branches);
                if (branches.size() > 1)
                {
                    StringBuilder sb = new StringBuilder(
                            "Family " + family + " has checkouts "
                            + "on heterogenous branches:");
                    for (Branch b : branches)
                    {
                        boolean emitted = false;
                        for (GitCheckout co : checkoutsForBranchName.get(b))
                        {
                            if (tree.checkoutsInProjectFamily(family)
                                    .contains(co))
                            {
                                if (!emitted)
                                {
                                    sb.append("\n  * ").append(b);
                                    emitted = true;
                                }
                                sb.append("\n    * ").append(co.loggingName());
                            }
                        }
                    }
                    into.add(sb.toString());
                }
                else
                    if (targetBranch != null)
                    {
                        for (Branch b : branches)
                        {
                            if (!b.name().equals(targetBranch))
                            {
                                StringBuilder sb = new StringBuilder(
                                        "All checkouts in family ")
                                        .append(family).append(
                                        " are not on the target branch '")
                                        .append(targetBranch)
                                        .append('\'');
                                for (GitCheckout co : checkoutsForBranchName
                                        .get(b))
                                {
                                    sb.append("\n    * ").append(co
                                            .loggingName()).append(
                                                    " is on branch ").append(b);
                                }
                                into.add(sb.toString());
                            }
                        }
                    }
            });
        }

        private void checkFamilies(Problems into)
        {
            Map<GitCheckout, Set<ProjectFamily>> familiesIn = new HashMap<>();
            Set<GitCheckout> containingSuperpoms = checkoutsContainingSuperpoms();
            boolean haveProblems = false;
            for (Pom pom : poms.poms())
            {
                GitCheckout co = tree.checkoutFor(pom);
                if (containingSuperpoms.contains(co))
                {
                    continue;
                }
                Set<ProjectFamily> fams = familiesIn.computeIfAbsent(co,
                        c -> new HashSet<>());
                fams.add(familyOf(pom));
                haveProblems |= fams.size() > 1;
            }
            if (haveProblems)
            {
                familiesIn.forEach((checkout, families) ->
                {
                    if (families.size() > 1)
                    {
                        into.add("Checkout is not a superpom set, but contains "
                                + "more than one family: " + families + " in "
                                + checkout.loggingName() + " @ "
                                + checkout.checkoutRoot());
                    }
                });
            }
        }

        private void checkLocalModifications(Problems into)
        {
            Set<GitCheckout> seen = new HashSet<>();
            for (Pom pom : poms().poms())
            {
                GitCheckout co = tree.checkoutFor(pom);
                if (co == null)
                {
                    continue;
                }
                if (seen.add(co))
                {
                    if (!co.equals(tree.root()) && tree.isDirty(co))
                    {
                        into.add("Have local modifications in " + co
                                .loggingName() + ": " + co.checkoutRoot());
                    }
                }
            }
        }

        private void checkDetachedHead(Problems into)
        {
            Set<GitCheckout> seen = new HashSet<>();
            for (Pom pom : poms().poms())
            {
                GitCheckout co = tree.checkoutFor(pom);
                if (seen.add(co))
                {
                    if (!tree.branches(co).currentBranch().isPresent())
                    {
                        into.add("Have detached head state in " + co
                                .loggingName() + ": " + co.checkoutRoot());
                    }
                }
            }
        }

        private void checkVersions(Problems into)
        {
            Map<ProjectFamily, PomVersion> versForFamily = versionsForFamilies();
            versForFamily.forEach((f, v) ->
            {
                log("Version for family " + f + ": " + v);
            });
            Map<ProjectFamily, Map<PomVersion, Set<Pom>>> inconsistencies = new HashMap<>();
            for (Pom pom : poms().poms())
            {
                if (pom.projectFolder().equals(tree.root().checkoutRoot()))
                {
                    // Root BOM is allowed to differ
                    continue;
                }
                ProjectFamily fam = ProjectFamily.familyOf(pom);
                if (!includeFamilies.contains(fam))
                {
                    continue;
                }
                PomVersion ver = versForFamily.get(fam);
                if (!Objects.equals(ver, pom.version()))
                {
                    Set<PomRole> roles = categorizer().rolesFor(pom);
                    if (roles.contains(JAVA) || (!roles.contains(CONFIG) && !roles
                            .contains(CONFIG_ROOT)))
                    {
                        Map<PomVersion, Set<Pom>> ics = inconsistencies
                                .computeIfAbsent(fam, f -> new TreeMap<>());
                        Set<Pom> poms = ics.computeIfAbsent(pom.version(),
                                v -> new TreeSet<>());
                        poms.add(pom);
                    }
                }
            }
            if (!inconsistencies.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                inconsistencies.forEach((fam, ics) ->
                {
                    if (sb.length() > 0)
                    {
                        sb.append("\n\n");
                    }
                    sb.append("Versions in family '").append(fam).append(
                            "' are inconsistent - expecting ").append(
                                    versForFamily.get(fam));
                    ics.forEach((ver, poms) ->
                    {
                        sb.append("\n  * ").append(ver);
                        poms.forEach(pom ->
                        {
                            Path relPath = tree.root().checkoutRoot()
                                    .relativize(pom.path());
                            sb.append("\n    * ").append(pom
                                    .toArtifactIdentifiers()).append("\t")
                                    .append(relPath);
                        });
                    });
                    Severity sev = tolerateVersionInconsistenciesIn
                            .contains(fam)
                                   ? Severity.NOTE
                                   : Severity.FATAL;
                    into.add(sb.toString(), sev);
                    sb.setLength(0);
                });
            }
        }

        private Map<ProjectFamily, PomVersion> versionsForFamilies()
        {
            if (versionsForFamilies != null)
            {
                return versionsForFamilies;
            }
            log("Collecting versions for families");
            Map<ProjectFamily, PomVersion> result = new HashMap<>();
            for (Pom pom : poms().poms())
            {
                ProjectFamily fam = ProjectFamily.familyOf(pom);
                result.computeIfAbsent(fam, f ->
                {
                    return f.probableFamilyVersion(poms.poms()).orElse(
                            PomVersion.UNKNOWN);

                });
            }
            return versionsForFamilies = result;
        }

        private void checkRoles(Problems into)
        {
            categorizer().eachPomAndItsRoles((pom, roles) ->
            {
                if (roles.contains(BILL_OF_MATERIALS) && roles.contains(CONFIG) && !roles
                        .contains(CONFIG_ROOT) && roles.contains(PARENT))
                {
                    if (categorizer().parentOf(pom).isPresent())
                    {
                        into.add(
                                "Intermediate pom " + pom
                                        .toArtifactIdentifiers() + " is acting as "
                                + "a bill-of-materials and a parent to other poms, but it has "
                                + "a parent.  Shared configuration should not live in a bill-of-materials "
                                + "unless it is also the root pom for its family: " + pom
                                        .path());
                    }
                }
            });
        }

        private Poms poms()
        {
            if (poms == null)
            {
                log("Collecting pom files");
                Set<Pom> allPoms = tree.allProjects()
                        .stream()
                        .filter(pom
                                -> familiesToCheck.test(ProjectFamily.familyOf(
                                pom)))
                        .filter(pom
                                -> tree.checkoutFor(pom) != null && reposFilter
                        .test(tree.checkoutFor(pom)))
                        .collect(toCollection(HashSet::new));
                log("Have " + allPoms.size() + " poms.");
                return poms = new Poms(allPoms);
            }
            return poms;
        }

        private PomCategorizer categorizer()
        {
            if (categorizer == null)
            {
                log("Categorizing pom files");
                categorizer = new PomCategorizer(poms());
            }
            return categorizer;
        }

        private void checkVersionFlavor(Problems result)
        {
            Map<VersionFlavor, Map<PomVersion, Set<Pom>>> inconsistencies = new EnumMap<>(
                    VersionFlavor.class);
            poms().poms().forEach(pom ->
            {
                if (!includeFamilies.contains(familyOf(pom)))
                {
                    return;
                }
                PomVersion ver = pom.version();
                if (!versionFlavor.equals(ver.flavor()))
                {
                    Map<PomVersion, Set<Pom>> inc = inconsistencies
                            .computeIfAbsent(ver.flavor(), v -> new TreeMap<>());
                    Set<Pom> set = inc
                            .computeIfAbsent(ver, v -> new TreeSet<>());
                    set.add(pom);
                }
            });
            if (!inconsistencies.isEmpty())
            {
                inconsistencies.forEach((flavor, pomsForVersion) ->
                {
                    StringBuilder sb = new StringBuilder(
                            "Not all projects have the version flavor '")
                            .append(versionFlavor).append('\'');
                    pomsForVersion.forEach((ver, poms) ->
                    {
                        sb.append("\n  * ").append(ver);
                        poms.forEach(pom ->
                        {
                            sb.append("\n    * ").append(pom
                                    .toArtifactIdentifiers()).append('\t')
                                    .append(tree.root().checkoutRoot()
                                            .relativize(pom.path()));
                        });
                    });
                    result.add(sb.toString());
                });
            }
        }
    }
}
