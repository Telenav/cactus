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
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.util.strings.Strings.camelCaseToDelimited;
import static com.mastfrog.util.strings.Strings.join;
import static com.telenav.cactus.git.GitCheckout.depthFirstCompare;
import static com.telenav.cactus.git.GitCheckout.isGitCommitId;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.*;
import static java.util.Collections.reverse;
import static java.util.Collections.sort;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

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
 * Like other mojos in this plugin, the set of repositories that are altered can
 * be controlled by the <code>cactus.scope</code> property, to apply to all git
 * repositories matching that scope - all sub-repositories are scanned, and
 * those containing maven projects matching the scope are selected.
 * </p>
 * <p>
 * Some useful one-liners using this mojo:
 * </p>
 * <ol>
 * <li>
 * Get all checkouts in the entire checkout tree down to the root git checkout
 * onto the default development branch:
 * <pre>
 *
 * mvn -Dcactus.scope=all -Dinclude-root=true -Dpermit-local-changes=true \
 *    com.telenav.cactus:cactus-maven-plugin:checkout
 *
 * </pre>
 * </li>
 * <li>Create a new feature branch named "feature/foo" for all projects in the
 * same family as the one you're invoking maven against, and switch to it, also
 * creating a branch in to submodule root, updating .gitmodules and pushing the
 * new branches:
 * <pre>
 *
 * mvn -Dcactus.scope=FAMILY -Dcreate-branches=true -Dinclude-root=true \
 *      -Dpush=true -Dtarget-branch=feature/foo -Dpermit-local-changes=true \
 *      com.telenav.cactus:cactus-maven-plugin:1.4.7:checkout
 *
 * </pre></li>
 * <li>Continuous-build use-case - you want to build <i>all</i> projects on a
 * new pull request or commit - get everything on a known-stable branch except
 * for one git submodule for which you're providing a separate PR branch name or
 * commit id:
 * <pre>
 *
 * mvn -Dcactus.scope=FAMILY -Dcreate-branches=false \
 *     -Dinclude-root=false -Doverride-branch-in=kivakit-stuff \
 *     -Doverride-branch-with=$PULL_REQUEST_REF \
 *      com.telenav.cactus:cactus-maven-plugin:1.4.7:checkout
 *
 * </pre>
 * </li>
 * </ol>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "SpellCheckingInspection"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "checkout", threadSafe = true)
@BaseMojoGoal("checkout")
public class CheckoutMojo extends ScopedCheckoutsMojo
{
    @SuppressWarnings("CodeBlock2Expr")
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
                    .loggingName());
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
            return -depthFirstCompare(checkout, o.checkout());
        }

        public boolean isNoOp()
        {
            return false;
        }

        @Override
        public String toString()
        {
            return getClass().getSimpleName() + "(" + checkout.name() + " -> "
                    + targetBranch + ")";
        }

        /**
         * Called after the entire batch has run.
         */
        protected void onPostRun() throws Exception
        {
            // do nothing
        }

        protected abstract boolean performBranchChange() throws Exception;

        protected void updateRootCheckout() throws Exception
        {
            if (succeeded() && !isSubmoduleRoot())
            {
                withSubmoduleRoot(subroot ->
                {
                    if (!isGitCommitId(targetBranch))
                    {
                        log.info(
                                "Update .gitmodules for " + checkout.name()
                                + " -> " + targetBranch);
                        checkout.submoduleRelativePath().ifPresent(path ->
                        {
                            subroot.setSubmoduleBranch(path.toString(),
                                    targetBranch);
                        });
                    }
                });
            }
            else
                if (isSubmoduleRoot() && checkout
                        .isDirtyIgnoringModifiedSubmodules())
                {
                    log.info(
                            "Generated local modifications in submodule root"
                            + " - creating a commit");
                    checkout.addAll();
                    // If the checkout is still dirty, then we didn't actually
                    // add anything.  That can happen if `git diff --quiet` exited
                    // 1 for the previous dirty test because a child git module
                    // shows the status "modified content".  In this state, dirty
                    // shows modifications, but there *is* nothing to add or commit
                    // yet, because 
                    checkout.commit(
                            "cactus-maven: Create or move branch " + targetBranch
                            + " to new heads in " + checkout.loggingName());

                }
        }

        protected final void withSubmoduleRoot(ThrowingConsumer<GitCheckout> c)
                throws Exception
        {
            if (tree.root().isSubmoduleRoot())
            {
                c.accept(tree.root());
            }
        }

        boolean canTolerateLocalChanges()
        {
            return false;
        }

        GitCheckout checkout()
        {
            return checkout;
        }

        boolean isRoot()
        {
            return isRoot;
        }

        Boolean isSubroot;

        boolean isSubmoduleRoot()
        {
            // We may be in a singleton checkout where there *is* not submodule root
            return isSubroot == null
                   ? isSubroot = isRoot && checkout.isSubmoduleRoot()
                   : isSubroot;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        boolean isUpdateRoot()
        {
            return updateRoot;
        }

        final void postRun() throws Exception
        {
            if (succeeded() && !pretend)
            {
                onPostRun();
            }
        }

        final void run() throws Exception
        {
            log.info(camelCaseToDelimited(getClass().getSimpleName(),
                    ' ') + " " + checkout.name() + " -> " + targetBranch);
            if (!pretend)
            {
                succeeded = performBranchChange();
                if (succeeded)
                {
                    tree.invalidateBranches(checkout);
                }
            }
        }

        boolean succeeded()
        {
            return succeeded;
        }

        String targetBranch()
        {
            return targetBranch;
        }

        void validate(Collection<? super String> problems) throws Exception
        {
            if ((!allowLocalChangesIfPossible || !canTolerateLocalChanges())
                    && tree.isDirty(checkout))
            {
                if (isSubmoduleRoot())
                {
                    return;
                }
                if (!tree.isDirtyIgnoringSubmoduleCommits(checkout))
                {
                    problems.add(
                            "Have local changes in " + checkout.loggingName()
                            + " - cannot proceed to change to branch " + targetBranch);
                }
            }
        }
    }

    private static final class CreateAndSwitchToBranch extends BranchingBehavior
    {
        private final String baseBranch;

        private final boolean push;

        CreateAndSwitchToBranch(
                ProjectTree tree,
                GitCheckout checkout,
                BuildLog log,
                String targetBranch,
                boolean pretend,
                String baseBranch,
                boolean allowLocalChangesIfPossible,
                boolean updateRoot,
                boolean push)
        {
            super(tree, checkout, log, targetBranch, pretend,
                    allowLocalChangesIfPossible, updateRoot);

            this.baseBranch = baseBranch;
            this.push = push;
        }

        @Override
        public String toString()
        {
            return "Create and switch to branch " + targetBranch + " based on "
                    + (baseBranch == null
                       ? " the current branch "
                       : baseBranch)
                    + " in " + checkout.loggingName()
                    + " push? " + push + " is-root? " + isSubmoduleRoot();
        }

        @Override
        protected void onPostRun() throws Exception
        {
            if (push)
            {
                checkout.pushCreatingBranch();
            }
            if (isUpdateRoot() && !checkout.isSubmoduleRoot() && checkout
                    .isSubmodule())
            {
                updateRootCheckout();
            }

        }

        @Override
        protected boolean performBranchChange()
        {
            try
            {
                checkout.createAndSwitchToBranch(targetBranch,
                        Optional.ofNullable(baseBranch));
                return true;
            }
            finally
            {
                tree.invalidateBranches(checkout);
            }
        }
    }

    private static final class DoNothing extends BranchingBehavior
    {
        DoNothing(ProjectTree tree, GitCheckout checkout,
                BuildLog log, String targetBranch)
        {
            super(tree, checkout, log, targetBranch, true, false, false);
        }

        @Override
        public boolean isNoOp()
        {
            return true;
        }

        @Override
        boolean canTolerateLocalChanges()
        {
            return true;
        }

        @Override
        protected boolean performBranchChange()
        {
            log.info(
                    "No change needed for " + checkout.name() + " -> "
                    + targetBranch);
            return true;
        }

        @Override
        void validate(Collection<? super String> problems)
        {
            // do nothing
        }
    }

    private static final class FailureBranching extends BranchingBehavior
    {
        private final String failure;

        FailureBranching(ProjectTree tree, GitCheckout checkout,
                BuildLog log, String targetBranch, boolean pretend,
                String failure)
        {
            super(tree, checkout, log, targetBranch, pretend, false, false);
            this.failure = failure;
        }

        @Override
        protected void onPostRun() throws Exception
        {
            throw new MojoFailureException("Should not be called for " + this);
        }

        @Override
        protected boolean performBranchChange() throws Exception
        {
            throw new MojoFailureException("Should not be called for " + this);
        }

        @Override
        void validate(
                Collection<? super String> problems)
        {
            problems.add(failure);
        }
    }

    private static final class PullOnly extends BranchingBehavior
    {
        PullOnly(ProjectTree tree, GitCheckout checkout,
                BuildLog log, String targetBranch)
        {
            super(tree, checkout, log, targetBranch, true, false, false);
        }

        @Override
        public boolean isNoOp()
        {
            return true;
        }

        @Override
        protected boolean performBranchChange()
        {
            if (checkout.needsPull())
            {
                try
                {
                    boolean root = tree.isSubmoduleRoot(checkout);
                    // We need to pull to ensure that a branch which was on the
                    // right branch but behind some commits gets yanked up to
                    // the branch head.
                    if (root)
                    {
                        log.info(
                                "Pull submodule root " + checkout.loggingName() + " on " + targetBranch
                                + " with --rebase");
                        checkout.pullWithRebase();
                    }
                    else
                    {
                        log.info(
                                "Pull " + checkout.loggingName() + " on " + targetBranch);
                        checkout.pull();
                    }
                }
                finally
                {
                    tree.invalidateBranches(checkout);
                }
            }
            return true;
        }

        @Override
        void validate(Collection<? super String> problems)
        {
            // do nothing
        }
    }

    private static final class SwitchToExistingLocalBranch extends BranchingBehavior
    {
        SwitchToExistingLocalBranch(ProjectTree tree,
                GitCheckout checkout, BuildLog log, String targetBranch,
                boolean pretend, boolean allowLocalChangesIfPossible,
                boolean updateRoot)
        {
            super(tree, checkout, log, targetBranch, pretend,
                    allowLocalChangesIfPossible, updateRoot);
        }

        @Override
        protected void onPostRun() throws Exception
        {
            // We may be back on a branch, but behind the fetch head, so pull.
            if (checkout.needsPull())
            {
                checkout.pull();
            }
            if (!isUpdateRoot())
            {
                return;
            }
            updateRootCheckout();
        }

        @Override
        protected boolean performBranchChange()
        {
            try
            {
                boolean result = checkout.switchToBranch(targetBranch);
                return true;
            }
            finally
            {
                tree.invalidateBranches(checkout);
            }
        }

        @Override
        boolean canTolerateLocalChanges()
        {
            return true;
        }
    }

    /**
     * If true, perform a git fetch before testing for branch existence so that
     * the set of remote branches is as accurate as possible.
     */
    @Parameter(property = "cactus.fetch-first",
            defaultValue = "true")
    private boolean fetchFirst;

    /**
     * If true, create branches if they do not exist.
     */
    @Parameter(property = CREATE_BRANCHES,
            defaultValue = "false")
    boolean createBranchesIfNeeded;

    /**
     * If true, create local branches in the case that a remote branch with the
     * same name already exists but a local one does not, but do not create new
     * branches from thin-air. This can be important in continuous builds where
     * there may be no tracking branch locally on a new clone, for the fallback
     * branch. Has no effect if createBranchesIfNeeded is true.
     */
    @Parameter(property = CREATE_LOCAL_BRANCHES,
            defaultValue = "false")
    boolean createLocalBranchesIfNeeded;

    /**
     * If we create new branches, push them to the remote immediately.
     */
    @Parameter(property = PUSH,
            defaultValue = "false")
    boolean push;

    /**
     * The target branch - this can either be an existing branch you want to get
     * all of the checkouts on, or if createBranchesIfNeeded is true, you want
     * to be created off the base branch.
     * <p>
     * If unset, the base branch is used as the target to get checkouts onto.
     */
    @Parameter(property = TARGET_BRANCH)
    String targetBranch;

    /**
     * The base branch which new feature-branches should be created from, and
     * which, if createBranchesIfNeeded is false, should be used as the fallback
     * branch to put checkouts on if the target branch does not exist.
     */
    @Parameter(property = BASE_BRANCH, defaultValue = DEFAULT_DEVELOPMENT_BRANCH,
            required = true)
    String baseBranch = DEFAULT_DEVELOPMENT_BRANCH;

    /**
     * For building a PR in a continuous build, there will be one git submodule
     * for which we may be passed a specific git commit ID we need to check out,
     * rather than just putting it on the branch-head.
     * <p>
     * This is the submodule path that should be put on the commit identified by
     * override-branch-with.
     * </p>
     */
    @Parameter(property = "cactus.override-branch-in")
    String overrideBranchSubmodule;

    /**
     * For building a PR in a continuous build, there will be one git submodule
     * for which we may be passed a specific git commit ID we need to check out,
     * rather than just putting it on the branch-head.
     * <p>
     * This is the branch name or commit ID the repo identified in
     * override-branch-in should be updated to.
     * </p>
     */
    @Parameter(property = "cactus.override-branch-with")
    String overrideBranchWith;

    /**
     * If true, attempt to switch branches, even in the presence of local
     * changes, if a local branch with the name of the target branch already
     * exists. This can fail if the local changes depend on changes from a head
     * other than the target branch's head commit. Ignored and the build files
     * if there are local changes <i>and</i> the branch exists remotely but not
     * locally.
     */
    @Parameter(property = PERMIT_LOCAL_CHANGES,
            defaultValue = "false")
    boolean permitLocalChanges = false;

    @SuppressWarnings(
            {
                "CodeBlock2Expr"
            })
    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        // Ensure we don't have any assets projects which have their own
        // branching scheme
        checkouts.removeAll(tree.nonMavenCheckouts());
        // Ensure that we either definitely do or definitely don't have
        // the submodule root, depending on the value of updateRoot
        includeOrRemoveRoot(checkouts, tree);
        // Perform a git fetch --all to ensure our branch lists are up-to-date
        if (fetchFirst)
        {
            fetchAll(checkouts);
            // In case the tree was used before, dump any cached branch lists
            checkouts.forEach(tree::invalidateBranches);
        }

        GitCheckout root = tree.root();
        if (checkouts.contains(root) && tree.isSubmoduleRoot(root))
        {
            checkouts.remove(root);
            checkouts.add(0, root);
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
            if (!behavior.isNoOp())
            {
                nonNoOpCount++;
            }
        }
        if (nonNoOpCount == 0)
        {
            log.info("All checkouts already on branch "
                    + (targetBranch == null
                       ? baseBranch
                       : targetBranch) + " or no such branch exists for them "
                    + "and we were not told to create one.");
            return;
        }
        // Make sure we apply our changes in deepest-first order, root checkout
        // first
        sort(changers);
        // Fail fast before we make any changes here.
        validateBehaviorsCanRun(changers);
        // Do the thing.
        for (BranchingBehavior beh : changers)
        {
            beh.run();
        }
        // Post work happens in the opposite order, root-last (so we commit
        // changes to the refs pointed to by the submodule parent only after
        // we have fully updated .gitmodules)
        reverse(changers);
        // This will update the submodule root, and set .gitmodules to point to
        // the right place if we're updating the root.
        for (BranchingBehavior beh : changers)
        {
            beh.postRun();
        }

        if ((this.createBranchesIfNeeded || this.createLocalBranchesIfNeeded)
                && this.isIncludeRoot()
                && tree.isSubmoduleRoot(root)
                && tree.isDirty(root))
        {

            // We may have dirtied the root again by updating .gitmodules - make sure that
            // gets committed
            CommitMessage msg = new CommitMessage(getClass(),
                    ".gitmodules update for " + targetBranch);
            msg.section("Branched", sec ->
            {
                changers.forEach(ch ->
                {
                    sec.bulletPoint(ch.checkout.loggingName() + ": " + ch);
                });
            });
            if (!isPretend())
            {
                root.addAll();
                root.commit(msg.toString());
            }
        }

        if (push && isIncludeRoot())
        {
            // Ensure we push our new branch if needed
            switch (root.needsPush())
            {
                case YES:
                    log.info("Push submodule root");
                    if (!isPretend())
                    {
                        tree.root().push();
                    }
                    break;
                case REMOTE_BRANCH_DOES_NOT_EXIST:
                    log.info("Push submodule root creating new branch "
                            + tree.root().branch());
                    if (!isPretend())
                    {
                        tree.root().pushCreatingBranch();
                    }
                    break;
            }
        }
    }

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

    @SuppressWarnings("SpellCheckingInspection")
    private BranchingBehavior branchChangerFor(ProjectTree tree,
            GitCheckout checkout, BuildLog log)
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

        if (isOverrideCheckout(checkout, tree))
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
            return new SwitchToExistingLocalBranch(tree, checkout, log,
                    overrideBranchWith, isPretend(), permitLocalChanges,
                    isIncludeRoot());
        }

        Branches br = tree.branches(checkout);
        Optional<Branch> baseOpt = baseBranchFor(checkout, tree,
                baseBranch, log);

        if (baseOpt.isEmpty())
        {
            // Do this so we can report ALL failures, not just bail out on
            // the first one.  The validate() method will apply our failure
            // message, and the build will abort before any changes are made.
            return new FailureBranching(tree, checkout, log,
                    baseBranch, isPretend(),
                    "No branch named '" + baseBranch + "' in " + checkout
                            .name() + ": " + br);
        }
        Branch base = baseOpt.get();

        Optional<Branch> current = br.currentBranch();
        log.info(
                checkout.name()
                + ": Base branch " + base.trackingName()
                + " current " + current + " create? " + createBranchesIfNeeded
                + " base " + baseBranch + " target " + targetBranch);
        // First case is we are just moving things to the base branch
        if (targetBranch == null)
        {
            // We are just trying to get on the "develop" or whatever the base is
            // branch
            if (base.isRemote())
            {
                // There is no local branch for "develop" or similar
                if (!createBranchesIfNeeded && !createLocalBranchesIfNeeded)
                {
                    return new FailureBranching(tree, checkout, log,
                            baseBranch, isPretend(),
                            "No local branch '" + baseBranch
                            + "' and createBranchesIfNeeded is false - will not create it.");
                }
                else
                {
                    // Create a local branch for the remote, e.g. refs/heads/develop
                    return new CreateAndSwitchToBranch(tree, checkout,
                            log,
                            this.baseBranch, isPretend(), base.trackingName(),
                            permitLocalChanges, isIncludeRoot(), push);
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
                        // Get us to the head of branch if we're not there
                        if (checkout.needsPull())
                        {
                            return new PullOnly(tree, checkout,
                                    log, this.baseBranch);
                        }
                        log.info("Use do nothing for " + checkout.loggingName());
                        // We're already on the right branch - do nothing
                        return new DoNothing(tree, checkout,
                                log, this.baseBranch);
                    }
                    else
                    {
                        // We are not on the branch, but it does exist locally,
                        // so switch to it
                        return new SwitchToExistingLocalBranch(tree,
                                checkout, log, base.name(), isPretend(),
                                permitLocalChanges, isIncludeRoot());
                    }
                }
                else
                {
                    // We are in detached head mode, but the local branch exists,
                    // so switch to it
                    return new SwitchToExistingLocalBranch(tree,
                            checkout, log, base.name(), isPretend(),
                            permitLocalChanges, isIncludeRoot());
                }
            }
        }
        else
        {
            String realTargetBranch = targetBranchFor(checkout, tree);
            // We have a branch to switch to or create
            if (current.isPresent() && current.get().name().equals(
                    realTargetBranch))
            {
                if (checkout.needsPull())
                {
                    // Get on the head of the target branch - we are on it but
                    // not on the head commit
                    return new PullOnly(tree, checkout, log,
                            this.baseBranch);
                }
                log.info(
                        "Use do nothing 2 for " + checkout.loggingName() + " target " + realTargetBranch);
                // We are already on the right branch, so do nothing
                return new DoNothing(tree, checkout, log,
                        targetBranch);
            }
            // Look for a branch with the right name, locally or remotely,
            // applying the heuristic that already-on-base-branch means to
            // incorporate any local commits into the new branch, and being
            // on some other branch means not to do thst.
            Optional<Branch> target = br.localOrRemoteBranch(realTargetBranch);

            if (target.isPresent())
            {
                // A branch with the right name exists somewhere
                Branch existingTargetBranch = target.get();
                if (existingTargetBranch.isLocal())
                {
                    // Local branch exists - just switch to it
                    return new SwitchToExistingLocalBranch(tree,
                            checkout, log,
                            realTargetBranch, isPretend(), permitLocalChanges,
                            isIncludeRoot());
                }
                else
                {
                    // Local branch does not exist but a remote one does.
                    if (createBranchesIfNeeded)
                    {
                        // Create a new branch tracking the remote one
                        return new CreateAndSwitchToBranch(tree,
                                checkout,
                                log, realTargetBranch, isPretend(),
                                existingTargetBranch.trackingName(),
                                permitLocalChanges, isIncludeRoot(), push);
                    }
                    else
                    {
                        // Remote branch exists, but we are not allowed to create
                        // a new local branch, so we just switch to the
                        // base branch we would otherwise create it off of
                        if (current.isPresent() && current.get().name().equals(
                                baseBranch))
                        {
                            if (checkout.needsPull())
                            {
                                return new PullOnly(tree,
                                        checkout, log, this.baseBranch);
                            }
                            log.info(
                                    "Use do nothing 3 for " + checkout
                                            .loggingName() + " current " + current
                                            .get());
                            // If already on the base branch, do nothing
                            return new DoNothing(tree, checkout,
                                    log, baseBranch);
                        }
                        // Just switch to the base branch, since we're not creating
                        // branches here
                        return new SwitchToExistingLocalBranch(tree,
                                checkout, log, baseBranch, isPretend(),
                                permitLocalChanges, isIncludeRoot());
                    }
                }
            }
            else
            {
                // The target branch does not exist locally OR remotely - we
                // are creating, say, a new feature branch from scratch.
                if (createBranchesIfNeeded)
                {
                    // For the submodule root, we do not want to fail in the case
                    // that we are dirty only by virtue of having some unfamiliar
                    // commits in submodules
                    boolean isRoot = tree.isSubmoduleRoot(checkout);
                    boolean localChangesOk = permitLocalChanges || isRoot;

                    // A heuristic:  If the checkout is already *on* the base
                    // branch, then null out the base branch so branching is done
                    // from the local tree's branch, not the remote head of the
                    // named base branch.  That 
                    String theBase = base.trackingName();
                    if (current.isPresent() && current.get().name().equals(base
                            .name()))
                    {
                        log.info(
                                "Current branch for " + checkout.loggingName() + " is same as "
                                + " " + base + " will branch off of the local branch, not the remote head.");
                        theBase = null;
                        localChangesOk = true;
                    }

                    // In the case that we could abort due to local changes in the
                    // submodule root which are simply new commits in submodules,
                    // new branches are going to change that anyway, so simply branch
                    // off of the local branch of the submodule root and allow such
                    // changes
                    if (theBase != null
                            && isRoot
                            && tree.isDirty(checkout)
                            && current.isPresent())
                    {
                        theBase = null;
                        localChangesOk = true;
                    }

                    // Create the new branch and switch to it
                    return new CreateAndSwitchToBranch(tree, checkout,
                            log, realTargetBranch, isPretend(),
                            theBase,
                            localChangesOk,
                            isIncludeRoot(),
                            push);
                }
                else
                {
                    // We are not allowed to create branches
                    if (current.isPresent() && current.get().name().equals(
                            baseBranch))
                    {
                        // Already on the right branch, do nothing
                        return new DoNothing(tree, checkout,
                                log, baseBranch);
                    }
                    // Can't create a branch, and no branch to move to.
                    // Give up.
                    return new FailureBranching(tree, checkout, log,
                            targetBranch, isPretend(),
                            "Branch '" + targetBranch + "' "
                            + "does not exist locally or remotely for "
                            + checkout.loggingName() + ", and "
                            + "createBranchesIfNeeded is false, so we "
                            + "will not create it.");
                }
            }
        }
    }

    /**
     * Applies the heuristic that if the checkout is on the base branch, the
     * local head is what should be branched from, but if it is on some other
     * branch, then the remote head is what should be branched from. This avoids
     * the case where unrelated changes that have not yet been merged get
     * stirred into a pull request by accident.
     *
     * @param checkout A checkout
     * @param tree A tree
     * @param realTargetBranch The target branch name (which may or may not yet
     * exist)
     * @param log A logger
     * @return A branch if any is matched, preferring remote or local according
     * to the heuristic
     */
    private Optional<Branch> baseBranchFor(GitCheckout checkout,
            ProjectTree tree, String realTargetBranch, BuildLog log)
    {
        Branches br = tree.branches(checkout);
        Optional<Branch> currOpt = br.currentBranch();
        if (!currOpt.isPresent())
        {
            log.info(
                    checkout.loggingName() + " is not on any branch - will prefer "
                    + "the remote head of " + baseBranch + " to branch from.");
            // Prefer the remote branch, fall back to the local branch,
            // if we are in detached-head currently
            return br.find(realTargetBranch, false)
                    .or(() -> br.find(realTargetBranch, true));
        }
        else
        {
            // If we are on the base branch already, assume any local changes
            // there should be incorporated into the new branch.  If we are on
            // some other branch, then base off of the remote branch
            Branch curr = currOpt.get();
            if (curr.name().equals(baseBranch))
            {
                log.info(checkout.loggingName() + " already on the "
                        + "base branch " + baseBranch + ".  Will create branch from "
                        + "the local, not remote head of it.");

                // If we are already on the base branch, prefer the
                // local target branch if one exists, falling back to the
                // remote branch - being on the base branch is a signal that
                // you want your new branch to include changes from the remote
                return br.find(realTargetBranch, true)
                        .or(() -> br.find(realTargetBranch, false));

            }
            else
            {
                log.info(checkout.loggingName() + " is not on the "
                        + "base branch " + baseBranch + ".  Will create branch from "
                        + "the remote, not local head of it, in case there are changes "
                        + "locally that should not be incorporated into that branch.");

                // Prefer the remote branch, fall back to the local branch
                return br.find(realTargetBranch, false)
                        .or(() -> br.find(realTargetBranch, true));
            }
        }
    }

    private void fetchAll(List<GitCheckout> checkouts)
    {
        for (GitCheckout co : checkouts)
        {
            log().info("Fetch all in " + co.loggingName());
            co.fetchAll();
        }
    }

    private void includeOrRemoveRoot(List<GitCheckout> checkouts,
            ProjectTree tree)
    {
        // If we are going to update the root, then ensure it's included
        // in the set of repos;  if we're definitely not going to, then
        // ensure it's NOT there.
        if (isIncludeRoot())
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
    }

    private boolean isOverrideCheckout(GitCheckout checkout, ProjectTree tree)
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
        if (overrideBranchSubmodule != null && !tree.isSubmoduleRoot(checkout))
        {
            String relPath = checkout.submoduleRelativePath()
                    .map(Path::toString).orElse("---");
            return overrideBranchSubmodule.equals(checkout.name()) || relPath
                    .equals(overrideBranchSubmodule);
        }
        return false;
    }

    private String targetBranch(GitCheckout checkout, ProjectTree tree)
    {
        if (overrideBranchSubmodule != null && !tree.isSubmoduleRoot(checkout))
        {
            String relPath = checkout.submoduleRelativePath()
                    .map(Path::toString).orElse("---");
            if (overrideBranchSubmodule.equals(checkout.name()) || relPath
                    .equals(overrideBranchSubmodule))
            {
                return overrideBranchWith;
            }
        }
        return targetBranch;
    }

    private String targetBranchFor(GitCheckout checkout, ProjectTree tree)
    {
        String tb = targetBranch(checkout, tree);
        return tb == null
               ? baseBranch
               : tb;
    }

    private void validateBehaviorsCanRun(List<BranchingBehavior> changers)
            throws Exception
    {
        // Checks if there are local modifications or other reasons something
        // would fail if we tried to make the changes
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
                    + join("\n", problems));
        }
    }
}
