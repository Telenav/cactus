package com.telenav.cactus.maven.tree;

import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.refactoring.PomCategorizer;
import com.telenav.cactus.maven.refactoring.PomRole;
import com.telenav.cactus.scope.ProjectFamily;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final Set<ProjectFamily> familiesToCheck = new HashSet<>();
    private Predicate<GitCheckout> checkoutsFilter = _ignored -> true;
    private Consumer<String> activityLogger = System.out::println;

    public static void main(String[] args) throws Exception
    {
        ConsistencyChecker2 cc = new ConsistencyChecker2().allChecks() //                .checkFamily(ProjectFamily.named("kivakit"))
                ;

        ProjectTree tree = new ProjectTree(GitCheckout.repository(Paths.get(
                "/tmp/jonstuff")).get());
//                "/Users/timb/work/telenav/jonstuff")).get());
        Problems p = cc.check(tree);

        System.out.println("PROBLEMS\n");
        System.out.println(p);
    }

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
        checkoutsFilter = checkoutsFilter.and(checkout -> ch.contains(checkout));
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
        Predicate<? super ProjectFamily> familyFilter = familyFilter();
        return new Checker(checkFamilies, checkRoles, checkVersions,
                checkDetached, checkBranches, checkRelativePaths,
                checkLocalModifications, checkRemoteModifications, familyFilter,
                checkoutsFilter, tree, activityLogger).check();
    }

    private Predicate<? super ProjectFamily> familyFilter()
    {
        Predicate<ProjectFamily> familyFilter;
        if (familiesToCheck.isEmpty())
        {
            familyFilter = _ignored -> true;
        }
        else
        {
            Set<ProjectFamily> defensiveCopy = new HashSet<>(familiesToCheck);
            familyFilter = family -> defensiveCopy.contains(family);
        }
        return familyFilter;
    }

    static final class Checker
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
        private Poms poms;
        private PomCategorizer categorizer;
        private Map<ProjectFamily, PomVersion> versionsForFamilies;
        private final Consumer<String> log;
        private Set<GitCheckout> checkoutsContainingSuperpoms;

        public Checker(boolean checkFamilies, boolean checkRoles,
                boolean checkVersions, boolean checkDetached,
                boolean checkBranches, boolean checkRelativePaths,
                boolean checkLocalModifications,
                boolean checkRemoteModifications,
                Predicate<? super ProjectFamily> familiesToCheck,
                Predicate<? super GitCheckout> reposFilter,
                ProjectTree tree,
                Consumer<String> log)
        {
            this.checkFamilies = checkFamilies;
            this.checkRoles = checkRoles;
            this.checkVersions = checkVersions;
            this.checkDetached = checkDetached;
            this.checkBranches = checkBranches;
            this.checkRelativePaths = checkRelativePaths;
            this.checkLocalModifications = checkLocalModifications;
            this.familiesToCheck = familiesToCheck;
            this.reposFilter = reposFilter;
            this.checkRemoteModifications = checkRemoteModifications;
            this.tree = tree;
            this.log = log;
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
            return result;
        }

        private Set<GitCheckout> checkoutsContainingSuperpoms()
        {
            if (checkoutsContainingSuperpoms != null)
            {
                return checkoutsContainingSuperpoms;
            }
            checkoutsContainingSuperpoms = new HashSet<>();
            Set<GitCheckout> seen = new HashSet<>();
            for (Pom pom : poms().poms())
            {
                GitCheckout co = tree.checkoutFor(pom);
                if (seen.add(co))
                {
                    if (categorizer().is(pom, CONFIG_ROOT))
                    {
                        checkoutsContainingSuperpoms.add(co);
                    }
                }
            }
            checkoutsContainingSuperpoms.add(tree.root());
            log("Checkouts containing superpoms: "
                    + checkoutsContainingSuperpoms);
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
                    into.add("Remote changes exist for " + co.logggingName()
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
                        sb.append("  * ").append(b);
                        for (GitCheckout co : checkoutsForBranchName.get(b))
                        {
                            sb.append("\n    * ").append(co.logggingName());
                        }
                    }
                    into.add(sb.toString());
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
                                + checkout.logggingName() + " @ "
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
                                .logggingName() + ": " + co.checkoutRoot());
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
                                .logggingName() + ": " + co.checkoutRoot());
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
            for (Pom pom : poms().poms())
            {
                if (pom.projectFolder().equals(tree.root().checkoutRoot()))
                {
                    // Root BOM is allowed to differ
                    continue;
                }
                ProjectFamily fam = ProjectFamily.familyOf(pom);
                PomVersion ver = versForFamily.get(fam);
                if (!Objects.equals(ver, pom.version()))
                {
                    Set<PomRole> roles = categorizer().rolesFor(pom);
                    if (roles.contains(JAVA) || (!roles.contains(CONFIG) && !roles
                            .contains(CONFIG_ROOT)))
                    {
                        into.add(
                                "Versions in family " + fam + " are inconsistent in a POM which is not "
                                + "a superpom - expected " + ver + " got " + pom
                                        .version()
                                + " for " + pom.toArtifactIdentifiers() + " in " + pom
                                .path());
                    }
                }
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
    }
}
