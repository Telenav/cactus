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

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.git.Branches;
import com.telenav.cactus.maven.git.Branches.Branch;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Multi-repo git branching swiss-army-knife. Uses:
 * <ul>
 * <li>You ran <code>git submodule update</code> and now you have a bunch of git
 * submodules in "detached head" state - use this to get them all onto the head
 * of a specific branch.</li>
 * <li>You want to create a new feature-branch (or whatever branch) across a set
 * of submodules, optionally creating them if needed</li>
 * <li>You have a continuous build, and you want it to get all submodules onto a
 * known branch, except for the one that received a pull request, which should
 * end up on the commit corresponding to the pull request (use
 * <code>override-branch-in</code> and <code>override-branch-with</code>)</li>
 * <li>You just want to switch branches across a bunch of stuff and the branches
 * already exist (at least remotely)</li>
 * <li>You have a local feature branch, and need to fill out similarly named
 * feature branches for other git submodules in the same (or every) family</li>
 * </ul>
 * <p>
 *
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "dev-prep", threadSafe = true)
public class DevelopmentPrepMojo extends ScopedCheckoutsMojo
{

    /**
     * If true, perform a git fetch before testing for branch existence so that
     * the set of remote branches is as accurate as possible.
     */
    @Parameter(property = "fetch-first",
            defaultValue = "true")
    private boolean fetchFirst;

    /**
     * If true, create branches if they do not exist.
     */
    @Parameter(property = "create-branches",
            defaultValue = "false")
    boolean createBranchesIfNeeded = false;

    /**
     * If true, update <code>.gitmodules</code> in the submodule root to spell
     * out the new branches for those checkouts that changed, and generate a
     * commit in it.
     */
    @Parameter(property = "update-root",
            defaultValue = "false")
    boolean updateRoot = false;

    /**
     * If we create new branches, push them to the remote immediately.
     */
    @Parameter(property = "push",
            defaultValue = "false")
    boolean push;

    /**
     * The target branch - this can either be an existing branch you want to get
     * all of the checkouts on, or if createBranchesIfNeeded is true, you want
     * to be created off the base branch.
     * <p>
     * If unset, the base branch is used as the target to get checkouts onto.
     */
    @Parameter(property = "target-branch", required = false)
    String targetBranch;

    /**
     * The base branch which new feature-branches should be created from, and
     * which, if createBranchesIfNeeded is false, should be used as the fallback
     * branch to put checkouts on if the target branch does not exist.
     */
    @Parameter(property = "base-branch", defaultValue = "develop",
            required = true)
    String baseBranch = "develop";

    /**
     * For buliding a PR in a continuous build, there will be one git submodule
     * for which we may be passed a specific git commit ID we need to check out,
     * rather than just putting it on the branch-head.
     * <p>
     * This is the submodule path that should be put on the commit identified by
     * override-branch-with.
     * </p>
     */
    @Parameter(property = "override-branch-in", required = false)
    String overrideBranchSubmodule;

    /**
     * For buliding a PR in a continuous build, there will be one git submodule
     * for which we may be passed a specific git commit ID we need to check out,
     * rather than just putting it on the branch-head.
     * <p>
     * This is the branch name or commit ID the repo identified in
     * override-branch-in should be updated to.
     * </p>
     */
    @Parameter(property = "override-branch-with", required = false)
    String overrideBranchWith;

    /**
     * If true, attempt to switch branches, even in the presence of local
     * changes, if a local branch with the name of the target branch already
     * exists. This can fail if the local changes depend on changes from a head
     * other than the target branch's head commit. Ignored and the build files
     * if there are local changes <i>and</i> the branch exists remotely but not
     * locally.
     */
    @Parameter(property = "permit-local-changes",
            defaultValue = "false")
    boolean permitLocalChanges = false;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        validateBranchName(targetBranch, true);
        validateBranchName(baseBranch, false);
        validateBranchName(overrideBranchWith, true);
        if ((overrideBranchWith == null) != (overrideBranchSubmodule == null))
        {
            fail("Either override-branch-in and override-branch-with must "
                    + "BOTH be set, or neither, but have overrideBranchSubmodule="
                    + overrideBranchSubmodule + " and overrideBranchWith="
                    + overrideBranchWith);
        }
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        // If we are going to update the root, then ensure it's included
        // in the set of repos;  if we're definitely not going to, then
        // ensure it's NOT there.
        if (updateRoot)
        {
            if (!checkouts.contains(tree.root()))
            {
                checkouts.add(0, tree.root());
            }
        }
        else
        {
            checkouts.remove(tree.root());
        }
        // Perform a git fetch --all to ensure our branch lists are up-to-date
        if (fetchFirst)
        {
            fetchAll(checkouts);
            // In case the tree was used before, dump any cached branch lists
            tree.invalidateCache();
        }

        // Create a branch changer for each repo
        List<BranchingBehavior> changers = new ArrayList<>();
        // It may be that all repos are already in the desired state and there
        // is nothing to do, so keep a count of how many repos will actually
        // do something, so we can bail out doing nothing if there's really
        // nothing to do.
        int nonNoOpCount = 0;
        for (GitCheckout co : checkouts)
        {
            BranchingBehavior behavior = branchChangerFor(tree, co,
                    log);
            changers.add(behavior);
            // Ignore the submodule root - it will always want to do something
            if (!behavior.isNoOp() && !behavior.isSubmoduleRoot())
            {
                nonNoOpCount++;
            }
        }
        if (nonNoOpCount == 0)
        {
            log.info("All checkouts already on branch "
                    + (targetBranch == null
                       ? baseBranch
                       : targetBranch));
            return;
        }
        // Make sure we apply our changes in deepest-first order, root checkout
        // first
        Collections.sort(changers);
        // Fail fast before we make any changes here.
        validateBehaviorsCanRun(changers);
        // Do the thing.
        for (BranchingBehavior beh : changers)
        {
            beh.run();
        }
        // Nuke any cached branch info
        tree.invalidateCache();
        // Post work happens in the opposite order, root-last (so we commit
        // changes to the refs pointed to by the submodule parent only after
        // we have fully updated .gitmodules)
        Collections.reverse(changers);
        for (BranchingBehavior beh : changers)
        {
            beh.postRun();
        }
    }

    private void validateBehaviorsCanRun(List<BranchingBehavior> changers)
            throws Exception, MojoExecutionException
    {
        List<String> problems = new ArrayList<>();
        for (BranchingBehavior beh : changers)
        {
            beh.validate(problems);
        }
        if (!problems.isEmpty())
        {
            fail("Cannot change all branches to '" + (targetBranch == null
                                                      ? baseBranch
                                                      : targetBranch) + ":\n"
                    + Strings.join("\n", problems));
        }
    }

    private void fetchAll(List<GitCheckout> checkouts)
    {
        for (GitCheckout co : checkouts)
        {
            co.fetchAll();
        }
    }

    private static abstract class BranchingBehavior implements
            Comparable<BranchingBehavior>
    {
        protected final ProjectTree tree;
        protected final GitCheckout checkout;
        protected final BuildLog log;
        protected final String targetBranch;
        protected final boolean pretend;
        private boolean succeeded;
        private final boolean allowLocalChangesIfPossible;
        private final boolean isRoot;
        private final boolean updateRoot;

        protected BranchingBehavior(ProjectTree tree, GitCheckout checkout,
                BuildLog log, String targetBranch, boolean pretend,
                boolean allowLocalChangesIfPossible, boolean updateRoot)
        {
            this.tree = tree;
            this.checkout = checkout;
            this.targetBranch = targetBranch;
            this.pretend = pretend;
            this.updateRoot = updateRoot;
            this.allowLocalChangesIfPossible = allowLocalChangesIfPossible;
            isRoot = checkout.equals(tree.root());
            this.log = log.child(getClass().getSimpleName()).child(checkout
                    .name());
        }

        protected final void withSubmoduleRoot(ThrowingConsumer<GitCheckout> c)
                throws Exception
        {
            if (tree.root().isSubmoduleRoot())
            {
                c.accept(tree.root());
            }
        }

        boolean isUpdateRoot()
        {
            return updateRoot;
        }

        boolean isRoot()
        {
            return isRoot;
        }

        boolean isSubmoduleRoot()
        {
            // We may be in a singleton checkout where there *is* not submodule root
            return isRoot && checkout.isSubmoduleRoot();
        }

        GitCheckout checkout()
        {
            return checkout;
        }

        @Override
        public String toString()
        {
            return getClass().getSimpleName() + "(" + checkout.name() + " -> "
                    + targetBranch + ")";
        }

        boolean canTolerateLocalChanges()
        {
            return false;
        }

        void validate(Collection<? super String> problems) throws Exception
        {
            if ((!allowLocalChangesIfPossible || !canTolerateLocalChanges()) && tree
                    .isDirty(checkout))
            {
                if (isSubmoduleRoot())
                {
                    return;
                }
                problems.add(
                        "Have local changes in " + checkout.name()
                        + " - cannot proceed to change to branch " + targetBranch);
            }
        }

        String targetBranch()
        {
            return targetBranch;
        }

        boolean succeeded()
        {
            return succeeded;
        }

        final void run() throws Exception
        {
            log.info(Strings.camelCaseToDelimited(getClass().getSimpleName(),
                    ' ') + " " + checkout.name() + " -> " + targetBranch);
            if (!pretend)
            {
                succeeded = performBranchChange();
            }
        }

        protected abstract boolean performBranchChange() throws Exception;

        final void postRun() throws Exception
        {
            if (succeeded() && !pretend)
            {
                onPostRun();
            }
        }

        /**
         * Called after the entire batch has run.
         */
        protected void onPostRun() throws Exception
        {
            // do nothing
        }

        @Override
        public int compareTo(BranchingBehavior o)
        {
            // The root checkout comes first in this case
            if (isRoot != o.isRoot)
            {
                if (isRoot)
                {
                    return -1;
                }
                else
                {
                    return 1;
                }
            }
            return -GitCheckout.depthFirstCompare(checkout, o.checkout());
        }

        public boolean isNoOp()
        {
            return false;
        }
    }

    private static final class DoNothingBranchingBehavior extends BranchingBehavior
    {
        public DoNothingBranchingBehavior(ProjectTree tree, GitCheckout checkout,
                BuildLog log, String targetBranch)
        {
            super(tree, checkout, log, targetBranch, true, false, false);
        }

        @Override
        void validate(Collection<? super String> problems) throws Exception
        {
            // do nothing
        }

        @Override
        protected boolean performBranchChange()
        {
            log.info(
                    "No change needed for " + checkout.name() + " -> " + targetBranch);
            return true;
        }

        @Override
        public boolean isNoOp()
        {
            return true;
        }
    }

    private static final class FailureBranchingBehavior extends BranchingBehavior
    {
        private final String failure;

        public FailureBranchingBehavior(ProjectTree tree, GitCheckout checkout,
                BuildLog log, String targetBranch, boolean pretend,
                String failure)
        {
            super(tree, checkout, log, targetBranch, pretend, false, false);
            this.failure = failure;
        }

        @Override
        void validate(
                Collection<? super String> problems) throws Exception
        {
            problems.add(failure);
        }

        @Override
        protected boolean performBranchChange() throws Exception
        {
            throw new MojoFailureException("Should not be called for " + this);
        }

    }

    private static final class SwitchToExistingLocalBranchBehavior extends BranchingBehavior
    {
        public SwitchToExistingLocalBranchBehavior(ProjectTree tree,
                GitCheckout checkout, BuildLog log, String targetBranch,
                boolean pretend, boolean allowLocalChangesIfPossible,
                boolean updateRoot)
        {
            super(tree, checkout, log, targetBranch, pretend,
                    allowLocalChangesIfPossible, updateRoot);
        }

        @Override
        boolean canTolerateLocalChanges()
        {
            return true;
        }

        @Override
        protected boolean performBranchChange() throws Exception
        {
            checkout.switchToBranch(targetBranch);
            return true;
        }

        @Override
        protected void onPostRun() throws Exception
        {
            if (!isUpdateRoot())
            {
                return;
            }
            if (succeeded() && !isSubmoduleRoot())
            {
                withSubmoduleRoot(subroot ->
                {
                    log.info(
                            "Update .gitmodules for " + checkout.name() + " -> " + targetBranch);
                    checkout.submoduleRelativePath().ifPresent(path ->
                    {
                        subroot.setSubmoduleBranch(path.toString(),
                                targetBranch);
                    });
                });
            }
            else
                if (isSubmoduleRoot() && checkout.isDirty())
                {
                    log.info(
                            "Generated local modifications in submodule root - creating a commit");
                    checkout.addAll();
                    checkout.commit(
                            "Create or move branch " + targetBranch + " to new heads");
                }
        }
    }

    private static final class CreateAndSwitchToBranchBehavior extends BranchingBehavior
    {
        private final String baseBranch;

        public CreateAndSwitchToBranchBehavior(ProjectTree tree,
                GitCheckout checkout, BuildLog log, String targetBranch,
                boolean pretend, String baseBranch,
                boolean allowLocalChangesIfPossible, boolean updateRoot)
        {
            super(tree, checkout, log, targetBranch, pretend,
                    allowLocalChangesIfPossible, updateRoot);
            this.baseBranch = baseBranch;
        }

        @Override
        protected boolean performBranchChange() throws Exception
        {
            checkout.createAndSwitchToBranch(targetBranch, Optional.of(
                    baseBranch));
            return true;
        }

        @Override
        protected void onPostRun() throws Exception
        {
            if (!isUpdateRoot())
            {
                return;
            }
            if (succeeded() && !isSubmoduleRoot())
            {
                withSubmoduleRoot(subroot ->
                {
                    log.info(
                            "Update .gitmodules for " + checkout.name() + " -> " + targetBranch);
                    checkout.submoduleRelativePath().ifPresent(path ->
                    {
                        subroot
                                .setSubmoduleBranch(path.toString(),
                                        targetBranch);
                    });
                });
            }
            else
                if (isSubmoduleRoot() && checkout.isDirty())
                {
                    log.info(
                            "Generated local modifications in submodule root - creating a commit");
                    checkout.addAll();
                    checkout.commit(
                            "Create or move branch " + targetBranch + " to new heads");
                }
        }
    }

    private Optional<Branch> baseBranchFor(GitCheckout checkout,
            ProjectTree tree)
    {
        Branches br = tree.branches(checkout);
        return br.localOrRemoteBranch(baseBranch);
    }

    private String targetBranchFor(GitCheckout checkout)
    {
        String tb = targetBranch(checkout);
        return tb == null
               ? baseBranch
               : tb;
    }

    private String targetBranch(GitCheckout checkout)
    {
        if (overrideBranchSubmodule != null && !checkout.isSubmoduleRoot())
        {
            String relPath = checkout.submoduleRelativePath().map(p -> p
                    .toString()).orElse("---");
            if (overrideBranchSubmodule.equals(checkout.name()) || relPath
                    .equals(overrideBranchSubmodule))
            {
                return overrideBranchWith;
            }
        }
        return targetBranch;
    }

    private boolean isOverrideCheckout(GitCheckout checkout)
    {
        if (overrideBranchSubmodule != null && (Objects.equals(targetBranch,
                overrideBranchWith) || (targetBranch == null && baseBranch
                        .equals(overrideBranchWith))))
        {
            // If the override target is exactly the same as the branch
            // we would move to anyway, no need to bypass the existence
            // checks
            return false;
        }
        if (overrideBranchSubmodule != null && !checkout.isSubmoduleRoot())
        {
            String relPath = checkout.submoduleRelativePath().map(p -> p
                    .toString()).orElse("---");
            if (overrideBranchSubmodule.equals(checkout.name()) || relPath
                    .equals(overrideBranchSubmodule))
            {
                return true;
            }
        }
        return false;
    }

    private BranchingBehavior branchChangerFor(ProjectTree tree,
            GitCheckout checkout, BuildLog log) throws Exception
    {
        // Okay, the states we have to deal with:
        //  - Base branch neither exists remotely nor locally
        //    - This is a hard fail - we don't have a branch to fork off of
        //  - Base branch does not exist locally but does exist remotely
        //    - This is okay, but we need to pass its target name when branching
        //    - If target branch null, create local branch off remote and switch to it
        //    - If target branch non-null and not same, create local branch off 
        //      remote with target name and switch to it
        //  - There is no target branch - base branch _is_ the target branch
        //  - There is a target branch AND
        //    - Target branch exists locally (and probably remotely)
        //      - Just change to it
        //    - Target branch exists remotely but not locally
        //      - Create a new local branch off the remote
        //    - Target branch neither exists remotely nor locally, but base
        //      branch exists somewhere
        //      - Create a new target branch off the base branch
        //      - Push -u the new branch to the origin so tracking is set up
        //
        // Add to that that one submodule may be special and have its own branch
        // or commit id we should move it to, which is not the same as the target
        // everything else should land on.
        //
        // Root checkout handling is special and must be done FIRST initially
        // (so we don't alter what checkout child repos are on that we've
        // already changed), and LAST when calling onPostRun(), which may generate
        // a commit in the submodule root repo if the child repos have moved or
        // .gitmodules was updated

        if (isOverrideCheckout(checkout))
        {
            // This checkout matches the value of overrideBranchSubmodule - we
            // may have a commit id not a branch name to move to.
            log.info("Have override ref for '" + checkout.name()
                    + "' - bypassing checks and will attempt to blindly check out '"
                    + overrideBranchWith + " (presumably a PR).  This may fail.");
            // The override may be a git commit ID and not a branch name at all,
            // so bypass all of the branch existence checking below and Just Do It,
            // and if it fails then the commit we're trying to doesn't exist and
            // the build fails here.
            return new SwitchToExistingLocalBranchBehavior(tree, checkout, log,
                    overrideBranchWith, isPretend(), permitLocalChanges,
                    updateRoot);
        }

        Branches br = tree.branches(checkout);
        Optional<Branch> baseOpt = baseBranchFor(checkout, tree);
        if (!baseOpt.isPresent())
        {
            // Do this so we can report ALL failures, not just bail out on
            // the first one
            return new FailureBranchingBehavior(tree, checkout, log,
                    baseBranch, isPretend(),
                    "No branch named '" + baseBranch + "' in " + checkout
                            .name() + ": " + br);
        }
        Branch base = baseOpt.get();
        Optional<Branch> current = br.currentBranch();
        if (targetBranch == null)
        {
            // We are just trying to get on the "develop" or whatever the base is
            // branch
            if (base.isRemote())
            {
                // There is no local branch for "develop" or similar
                if (createBranchesIfNeeded)
                {
                    return new FailureBranchingBehavior(tree, checkout, log,
                            baseBranch, isPretend(),
                            "No local branch '" + baseBranch
                            + "' and createBranchesIfNeeded is false - will not create it.");
                }
                else
                {
                    // Create a local branch for the remote, e.g. refs/heads/develop
                    return new CreateAndSwitchToBranchBehavior(tree, checkout,
                            log,
                            this.baseBranch, isPretend(), base.trackingName(),
                            permitLocalChanges, updateRoot);
                }
            }
            else
            {
                // The base branch already exists locally, and that is what we
                // are switching to.
                if (current.isPresent())
                {
                    // Our checkout is already on some branch
                    if (current.get().equals(base))
                    {
                        // We're already on the right branch - do nothing
                        return new DoNothingBranchingBehavior(tree, checkout,
                                log, this.baseBranch);
                    }
                    else
                    {
                        // We are not on the branch, but it does exist locally,
                        // so switch to it
                        return new SwitchToExistingLocalBranchBehavior(tree,
                                checkout, log, base.name(), isPretend(),
                                permitLocalChanges, updateRoot);
                    }
                }
                else
                {
                    // We are in detached head mode, but the local branch exists,
                    // so switch to it
                    return new SwitchToExistingLocalBranchBehavior(tree,
                            checkout, log, base.name(), isPretend(),
                            permitLocalChanges, updateRoot);
                }
            }
        }
        else
        {
            String realTargetBranch = targetBranchFor(checkout);
            // We have a branch to switch to or create
            if (current.isPresent() && current.get().name().equals(
                    realTargetBranch))
            {
                // We are already on the right branch, so do nothing
                return new DoNothingBranchingBehavior(tree, checkout, log,
                        targetBranch);
            }
            // Look for a branch with teh right name, locally or remotely
            Optional<Branch> target = br.localOrRemoteBranch(realTargetBranch);
            if (target.isPresent())
            {
                // A branch with the right name exists somewhere
                Branch existingTargetBranch = target.get();
                if (existingTargetBranch.isLocal())
                {
                    // Local branch exists - just switch to it
                    return new SwitchToExistingLocalBranchBehavior(tree,
                            checkout, log,
                            realTargetBranch, isPretend(), permitLocalChanges,
                            updateRoot);
                }
                else
                {
                    // Local branch does not exist but a remote one does.
                    if (createBranchesIfNeeded)
                    {
                        // Create a new branch tracking the remote one
                        return new CreateAndSwitchToBranchBehavior(tree,
                                checkout,
                                log, realTargetBranch, isPretend(),
                                existingTargetBranch.trackingName(),
                                permitLocalChanges, updateRoot);
                    }
                    else
                    {
                        // Remote branch exists, but we are not allowed to create
                        // a new local branch, so we just switch to the
                        // base branch we would otherwise create it off of
                        if (current.isPresent() && current.get().name().equals(
                                baseBranch))
                        {
                            // If already on the base branch, do nothing
                            return new DoNothingBranchingBehavior(tree, checkout,
                                    log, baseBranch);
                        }
                        // Just switch to the base branch, since we're not creating
                        // branches here
                        return new SwitchToExistingLocalBranchBehavior(tree,
                                checkout, log, baseBranch, isPretend(),
                                permitLocalChanges, updateRoot);
                    }
                }
            }
            else
            {
                // The target branch does not exist locally OR remotely - we
                // are creating, say, a new feature branch from scratch.
                if (createBranchesIfNeeded)
                {
                    return new CreateAndSwitchToBranchBehavior(tree, checkout,
                            log, realTargetBranch, isPretend(), base
                                    .trackingName(), permitLocalChanges,
                            updateRoot);

                }
                else
                {
                    // We are not allowed to create branches
                    if (current.isPresent() && current.get().name().equals(
                            baseBranch))
                    {
                        // Already on the right branch, do nothing
                        return new DoNothingBranchingBehavior(tree, checkout,
                                log, baseBranch);
                    }
                    // Can't create a branch, and no branch to move to.
                    // Give up.
                    return new FailureBranchingBehavior(tree, checkout, log,
                            targetBranch, isPretend(),
                            "Branch '" + targetBranch + "' "
                            + "does not exist locally or remotely, and "
                            + "createBranchesIfNeeded is false, so we"
                            + "will not create it.");
                }
            }
        }
    }
}
