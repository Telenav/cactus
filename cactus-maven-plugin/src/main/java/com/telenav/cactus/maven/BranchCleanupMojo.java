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
package com.telenav.cactus.maven;

import com.telenav.cactus.cli.ProcessFailedException;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.task.TaskSet;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.task.TaskSet.newTaskSet;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableSet;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Cleans up remote branches which have been merged with one of a list of "safe"
 * remote branches, where the remote branch's name is not one of the safe
 * branches, and not in a list of "protected" branches.
 * <p>
 * This is useful for cleaning up already merged, defunct feature or bugfix
 * branches or similar.
 * </p><p>
 * The following branch names are hard coded to be "protected" and will
 * <i>never</i>
 * be deleted by this mojo:
 * </p>
 * <ul>
 * <li>master</li>
 * <li>develop</li>
 * <li>stable</li>
 * <li>release/current</li>
 * <li>Any branch whose name starts with <code>release/</code></li>
 * </ul>
 * <p>
 * Branches are only deleted from the <i>default</i> remote, in the case that
 * remotes are set up for more than one.
 * </p>
 * <p>
 * Local branch clean up is also possible, deleting local branches that have no
 * corresponding remote, and whose head commit exists on one or another safe
 * branch on the remote.
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "remote-branch-cleanup", threadSafe = true)
@BaseMojoGoal("remote-branch-cleanup")
public class BranchCleanupMojo extends ScopedCheckoutsMojo
{
    private static final Set<String> ALWAYS_PROTECTED
            = unmodifiableSet(new HashSet<>(asList("master", "develop",
                    "stable", "release/current", "publish")));

    /**
     * Comma delimited list of branches which should not be deleted, no matter
     * what.
     */
    @Parameter(property = "cactus.protected-branches", required = false)
    private String protectedBranches;

    /**
     * List of regular expressions.
     */
    @Parameter(property = "cactus.protected-branch-patterns", required = false)
    private List<String> protectedPatterns;

    /**
     * Comma delimited list of branches which developers merge down to. If one
     * of these contains the head commit of a remote branch, it is considered
     * safe to delete it.
     */
    @Parameter(property = "cactus.safe-branches",
            defaultValue = "develop,release/current,publish")
    private String safeBranches;

    /**
     * List of regular expressions.
     */
    @Parameter(property = "cactus.safe-branch-patterns",
            defaultValue = "develop,release/current,publish")
    private List<String> safePatterns;

    /**
     * Because this mojo could wreak quite a bit of havoc if used carelessly, a
     * reminder property that must be explicitly set to true, or this mojo stays
     * in "pretend" mode.
     */
    @Parameter(property = "cactus.i-understand-the-risks")
    private boolean acknowledged;

    /**
     * If true (the default), delete remote branches (regardless of whether
     * there is a corresponding local branch).
     */
    @Parameter(property = "cactus.cleanup-remote", defaultValue = "true")
    private boolean cleanupRemote;

    /**
     * If true (the default), delete local branches that DO NOT have a
     * corresponding remote branch, where those branches head commit exists in a
     * safe branch. This is useful for cleaning up extraneous local temporary
     * branches. Will never delete the branch the working tree is currently on,
     * or any branch with the name of a safe or protected branch.
     */
    @Parameter(property = "cactus.cleanup-local", defaultValue = "true")
    private boolean cleanupLocal;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        Set<String> safe = safeBranches();
        if (safe.isEmpty())
        {
            fail("Will not delete all remote branches");
        }
        safe.forEach(branch -> validateBranchName(branch, false));
        if (!cleanupRemote && !cleanupLocal)
        {
            log.warn("Both cactus.cleanup-remote and cactus.cleanup-local are "
                    + "false.  Nothing will be done.");
        }
        protectedPatterns();
        safePatterns();
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        if (!acknowledged)
        {
            log.warn(
                    "cactus.i-understand-the-risks not set - running in "
                    + "pretend-mode. No branches will actually be deleted");
        }

        TaskSet remoteTasks = newTaskSet(log);
        Predicate<String> protectedBranchFilter = protectedBranchFilter();
        Predicate<String> safeBranchFilter = safeBranchFilter();

        log.debug(protectedBranchFilter::toString);
        log.debug(safeBranchFilter::toString);

        if (cleanupRemote)
        {
            collectRemoteBranchesForCleanup(checkouts, protectedBranchFilter,
                    safeBranchFilter, tree, log, remoteTasks);
        }
        boolean hadTasks = !remoteTasks.isEmpty();
        remoteTasks.execute();

        if (hadTasks && acknowledged && !isPretend())
        {
            for (GitCheckout checkout : checkouts)
            {
                log.info(
                        "Refresh remote branches after making changes for "
                        + checkout.loggingName());
                tree.invalidateBranches(checkout);
                checkout.updateRemoteHeads();
                checkout.fetchPruningDefunctLocalRecordsOfRemoteBranches();
            }
        }

        // Deleting remote branches can obsolete some local branches that
        // were not obsolete before, so only collect local branches after
        // we have really deleted the remote branches that may correspond
        TaskSet localTasks = newTaskSet(log);
        if (cleanupLocal)
        {
            collectLocalBranchesForCleanup(checkouts, protectedBranchFilter,
                    safeBranchFilter, tree, log, localTasks);
        }
        hadTasks |= !localTasks.isEmpty();
        localTasks.execute();

        if (!hadTasks)
        {
            log.info("Nothing to do");
        }
        else
        {
            // The tree is shared, so clear its branch cache
            tree.invalidateCache();
        }
    }

    public void collectRemoteBranchesForCleanup(List<GitCheckout> checkouts,
            Predicate<String> protectedBranchFilter,
            Predicate<String> safeBranchNames,
            ProjectTree tree,
            BuildLog log1, TaskSet tasks)
    {
        collectRemoteBranches(checkouts, protectedBranchFilter, safeBranchNames,
                tree,
                (candidates) ->
        {
            if (candidates.isEmpty())
            {
                log1.info("No branches needing cleanup.");
                return;
            }
            Set<CheckoutAndHead> operateOn = filterToBranchesAlreadyMergedToSafeBranches(
                    candidates,
                    safeBranchNames,
                    tree, log1);
            if (operateOn.isEmpty())
            {
                log1.info(
                        "All candidates contain commits not on a safe branch.");
                return;
            }
            // So we log and work in a repeatable way
            List<CheckoutAndHead> sorted = new ArrayList<>(operateOn);
            sort(sorted);
            sorted.forEach(candidate ->
            {
                tasks.add("Delete " + candidate, () ->
                {
                    if (acknowledged)
                    {
                        ifNotPretending(() -> candidate.deleteBranch(
                                tree,
                                log1));
                    }
                });
            });
        });
    }

    public void collectLocalBranchesForCleanup(List<GitCheckout> checkouts,
            Predicate<String> protectedBranchFilter,
            Predicate<String> safeBranchNames,
            ProjectTree tree,
            BuildLog log, TaskSet tasks)
    {
        collectLocalBranches(checkouts, protectedBranchFilter, safeBranchNames,
                tree, localBranches ->
        {
            localBranches.forEach((branch, candidates) ->
            {
                candidates.forEach(checkoutAndHead ->
                {
                    Branches branches = tree.branches(checkoutAndHead.checkout);
                    Optional<Branch> opt = branches.currentBranch();
                    boolean canDelete;
                    if (!opt.isPresent())
                    {
                        canDelete = true;
                    }
                    else
                    {
                        canDelete = !opt.get().name().equals(
                                checkoutAndHead.branch.name());
                        if (!canDelete)
                        {
                            log.info(
                                    "Will not delete local branch " + checkoutAndHead
                                    + " because it is the current branch in the working tree.");

                        }
                    }
                    if (canDelete)
                    {
                        log.info(
                                "Will delete local branch " + checkoutAndHead.branch
                                        .name() + " in "
                                + checkoutAndHead.checkout.loggingName());

                        tasks.add(
                                "Delete local branch "
                                + checkoutAndHead.branch.name() + " in "
                                + checkoutAndHead.checkout.loggingName(), () ->
                        {
                            try
                            {
                                ifNotPretending(() ->
                                {
                                    checkoutAndHead.checkout.deleteBranch(
                                            checkoutAndHead.branch.name(), null,
                                            false);
                                });
                            }
                            catch (ProcessFailedException | CompletionException ex)
                            {
                                log.error(
                                        "Failed to delete " + checkoutAndHead + ": " + ex
                                                .getMessage());
                            }
                        });
                    }
                });
            });
        });
    }

    private Set<CheckoutAndHead> filterToBranchesAlreadyMergedToSafeBranches(
            Map<String, Set<CheckoutAndHead>> candidates,
            Predicate<String> safeBranchNames,
            ProjectTree tree, BuildLog log)
    {
        Set<CheckoutAndHead> result = new HashSet<>();
        Set<String> unclean = new HashSet<>();
        candidates.forEach((branchName, targets) ->
        {
            if (!unclean.contains(branchName))
            {
                targets.forEach(checkoutAndBranch ->
                {
                    Branches containingCommit = checkoutAndBranch
                            .branchesContainingHead();

                    boolean added = false;
                    for (Branch remoteBranch : containingCommit.remoteBranches())
                    {
                        if (remoteBranch.isLocal())
                        {
                            continue;
                        }
                        if (remoteBranch.isSameName(checkoutAndBranch.branch))
                        {
                            continue;
                        }
                        if (safeBranchNames.test(remoteBranch.name()))
                        {
                            log.debug(
                                    () -> "Head " + checkoutAndBranch.head + " of " + checkoutAndBranch
                                    + " is included in the safe branch " + remoteBranch + " so it is safe to delete.");
                            result.add(checkoutAndBranch);
                            added = true;
                            break;
                        }
                    }
                    if (!added)
                    {
                        log.info(
                                "Will not delete branch '"
                                + checkoutAndBranch.branch.trackingName()
                                + "' in "
                                + checkoutAndBranch.checkout.loggingName()
                                + " because no safe branch contains its head commit.");
                        unclean.add(branchName);
                    }
                });
            }
            else
            {
                log.info("Will not delete branch '" + branchName
                        + "' because another checkout in the tree of a branch "
                        + "with the same name has unpushed commits");

            }
        });
        for (Iterator<CheckoutAndHead> it = result.iterator(); it.hasNext();)
        {
            CheckoutAndHead ch = it.next();
            if (unclean.contains(ch.branch.name()))
            {
                log.debug(() -> "Prune " + ch 
                        + " from deletions because some checkout has unmerged chanegs on it");
                it.remove();
            }
            if (!ch.isFromDefaultRemote())
            {
                log.info(
                        "Skipping " + ch + " - it is not from the default remote");
                it.remove();
            }
        }
        return result;
    }

    void collectRemoteBranches(
            Collection<? extends GitCheckout> checkouts,
            Predicate<String> protectedBranchFilter,
            Predicate<String> safeBranchNames,
            ProjectTree tree,
            Consumer<Map<String, Set<CheckoutAndHead>>> c)
    {
        Map<String, Set<CheckoutAndHead>> result = new TreeMap<>();
        checkouts.forEach(checkout -> scanForBranches(checkout, result,
                safeBranchNames, tree, protectedBranchFilter));
        c.accept(result);
    }

    void collectLocalBranches(Collection<? extends GitCheckout> checkouts,
            Predicate<String> protectedBranchFilter,
            Predicate<String> safeBranchNames,
            ProjectTree tree,
            Consumer<Map<String, Set<CheckoutAndHead>>> c)
    {
        Map<String, Set<CheckoutAndHead>> result = new TreeMap<>();
        checkouts.forEach(checkout -> scanForLocalBranches(checkout, result,
                safeBranchNames, tree, protectedBranchFilter));
        c.accept(result);
    }

    private void scanForLocalBranches(GitCheckout checkout,
            Map<String, Set<CheckoutAndHead>> candidateBranches,
            Predicate<String> safeBranches,
            ProjectTree tree,
            Predicate<String> protectedBranchFilter)
    {
        Branches branches = tree.branches(checkout);
        branches.localBranches().forEach(branch ->
        {
            if (safeBranches.test(branch.name()) || protectedBranchFilter
                    .test(branch.name()))
            {
                return;
            }
            if (!branches.hasRemoteForLocalOrLocalForRemote(branch))
            {
                String head = checkout.headOf(branch.name());
                if (head != null)
                {
                    Branches containing = checkout.branchesContainingCommit(
                            head);
                    for (Branch remote : containing.remoteBranches())
                    {
                        if (safeBranches.test(remote.name()))
                        {
                            candidateBranches.computeIfAbsent(branch.name(),
                                    br -> new TreeSet<>())
                                    .add(new CheckoutAndHead(checkout, head,
                                            branch));
                            break;
                        }
                    }
                }
            }
        });
    }

    private void scanForBranches(GitCheckout checkout,
            Map<String, Set<CheckoutAndHead>> candidateBranches,
            Predicate<String> safeBranches,
            ProjectTree tree,
            Predicate<String> protectedBranchFilter)
    {
        log.info("Scan " + checkout.loggingName());
        ifNotPretending(() ->
        {
            checkout.updateRemoteHeads();
            checkout.fetchPruningDefunctLocalRecordsOfRemoteBranches();
            tree.invalidateBranches(checkout);
        });
        Branches branches = tree.branches(checkout);
        Set<Branch> remotes = branches.remoteBranches();

        for (Branch branch : remotes)
        {
            if (safeBranches.test(branch.name()))
            {
                continue;
            }
            else
                if (protectedBranchFilter.test(branch.name()))
                {
                    continue;
                }
            String head = checkout.headOf(branch.trackingName());
            if (head != null)
            {
                candidateBranches.computeIfAbsent(
                        branch.name(),
                        nm -> new HashSet<>())
                        .add(new CheckoutAndHead(checkout, head, branch));
            }
        }
    }

    class CheckoutAndHead implements Comparable<CheckoutAndHead>
    {
        private final GitCheckout checkout;
        private final String head;
        private final Branch branch;

        CheckoutAndHead(GitCheckout checkout, String head, Branch branch)
        {
            this.checkout = checkout;
            this.head = head;
            this.branch = branch;
        }

        public void deleteBranch(ProjectTree tree, BuildLog log)
        {
            boolean deleted;
            boolean failed = false;
            try
            {
                deleted = checkout.deleteRemoteBranch(checkout
                        .defaultRemote().get().name(),
                        branch.name());
            }
            catch (ProcessFailedException | CompletionException failure)
            {
                if (failure.getMessage() != null && failure.getMessage()
                        .contains("remote ref does not exist"))
                {
                    log.info(
                            "Remote branch " + branch + " was already deleted on the server.");
                    deleted = false;
                    failed = true;
                }
                else
                {
                    throw failure;
                }
            }
            if (deleted)
            {
                log.info("Deleted " + this);
                emitMessage("Deleted " + this);
            }
            else
            {
                if (!failed)
                {
                    log.info("Failed to delete " + this + ". Skipping.");
                }
                return;
            }
            Branches branches = tree.branches(checkout);
            branches.opposite(branch).ifPresent(localBranch ->
            {
                Optional<Branch> workingTreeBranch = branches.currentBranch();
                boolean canDeleteLocalBranch;
                if (workingTreeBranch.isPresent())
                {
                    canDeleteLocalBranch = !branch.isSameName(localBranch);
                }
                else
                {
                    canDeleteLocalBranch = false;
                }
                if (canDeleteLocalBranch)
                {
                    boolean deletedLocal = checkout.deleteBranch(localBranch
                            .name(), workingTreeBranch
                                    .get().name(), false);
                    if (deletedLocal)
                    {
                        log.info("Deleted local " + localBranch.name());
                        emitMessage(
                                "Deleted local " + localBranch.name());
                    }
                }
            });
        }

        @Override
        public String toString()
        {
            String remote = branch.remote().orElse("");
            return checkout.loggingName() + " " + remote
                    + (remote.isEmpty()
                       ? ""
                       : " ")
                    + branch.name();
        }

        public boolean isFromDefaultRemote()
        {
            return branch.remote().map(rem ->
            {
                return checkout.defaultRemote().map(remotes ->
                {
                    return remotes.name().equals(rem);
                }).orElse(false);
            }).orElse(false);
        }

        public Branches branchesContainingHead()
        {
            return checkout.branchesContainingCommit(head);
        }

        @Override
        public int compareTo(CheckoutAndHead o)
        {
            int result = checkout.compareTo(o.checkout);
            if (result == 0)
            {
                result = branch.compareTo(o.branch);
            }
            if (result == 0)
            {
                result = head.compareTo(o.head);
            }
            return result;
        }

        @Override
        public int hashCode()
        {
            int hash = 3;
            hash = 59 * hash + Objects.hashCode(this.checkout);
            hash = 59 * hash + Objects.hashCode(this.head);
            hash = 59 * hash + Objects.hashCode(this.branch);
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
            final CheckoutAndHead other = (CheckoutAndHead) obj;
            if (!Objects.equals(this.head, other.head))
                return false;
            if (!Objects.equals(this.checkout, other.checkout))
                return false;
            return Objects.equals(this.branch, other.branch);
        }
    }

    private Set<String> protectedBranches()
    {
        Set<String> result = new HashSet<>(ALWAYS_PROTECTED);
        if (protectedBranches != null)
        {
            for (String s : protectedBranches.split(","))
            {
                s = s.trim();
                result.add(s);
            }
        }
        return result;
    }

    private Predicate<String> safeBranchFilter()
    {
        return predicate("safe", safeBranches(), safePatterns());
    }

    private Set<String> safeBranches()
    {
        Set<String> all = new HashSet<>();
        for (String s : safeBranches.split(","))
        {
            all.add(s.trim());
        }
        return all;
    }

    private Predicate<String> protectedBranchFilter()
    {
        return predicate("protected", protectedBranches(), protectedPatterns());
    }

    private Set<Pattern> protectedPatterns()
    {
        Set<Pattern> result = new HashSet<>(patterns(protectedPatterns));
        result.add(Pattern.compile("^v?\\d+\\.\\d+\\[.$]?.*"));
        result.add(Pattern.compile("^release/*"));
        return result;
    }

    private Set<Pattern> safePatterns()
    {
        return patterns(safePatterns);
    }

    private Set<Pattern> patterns(Collection<? extends String> patterns)
    {
        if (patterns == null || patterns.isEmpty())
        {
            return emptySet();
        }
        Set<Pattern> result = new HashSet<>();
        for (String pat : protectedPatterns)
        {
            if (!pat.isBlank())
            {
                try
                {
                    result.add(Pattern.compile(pat));
                }
                catch (Exception ex)
                {
                    fail("Invalid regular expression '" + pat + "'");
                }
            }
        }
        return result;
    }

    static Predicate<String> predicate(String name,
            Collection<? extends String> exactMatches,
            Collection<? extends Pattern> patterns)
    {
        // This could be a lambda, but ... logging
        return new ExactAndPatternPredicate(name, exactMatches, patterns);
    }

    private static final class ExactAndPatternPredicate implements
            Predicate<String>
    {
        private final String name;
        private final Collection<? extends String> exactMatches;
        private final Collection<? extends Pattern> patterns;

        ExactAndPatternPredicate(
                String name,
                Collection<? extends String> exactMatches,
                Collection<? extends Pattern> patterns)
        {
            this.name = name;
            this.exactMatches = exactMatches;
            this.patterns = patterns;
        }

        @Override
        public boolean test(String branch)
        {
            if (exactMatches.contains(branch))
            {
                return true;
            }
            for (Pattern p : patterns)
            {
                if (p.matcher(branch).find())
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(name).append('(');
            int len = name.length() + 1;
            for (String p : exactMatches)
            {
                if (sb.length() > len)
                {
                    sb.append(",");
                }
                sb.append(p);
            }
            for (Pattern p : patterns)
            {
                if (sb.length() > len)
                {
                    sb.append(",");
                }
                sb.append('/').append(p.pattern()).append('/');
            }
            return sb.append(')').toString();
        }
    }
}
