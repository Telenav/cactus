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

import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Conflicts;
import com.telenav.cactus.git.Conflicts.Conflict;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.NeedPushResult;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.AutomergeTag;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicies;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.*;
import static java.util.Collections.emptySet;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Performs a git commit, with the passed <code>commit-message</code> which
 * <b>must</b> be supplied (try enclosing the -D argument entirely on the
 * command-line, e.g. <code>'-Dcommit-message=Some commit message'</code>).
 * <p>
 * The scope for which commits are generated is FAMILY by default, generating
 * commits for all git sub-repositories of the subrepo parent which share a
 * project family (derived from the project's groupId). Passing ALL will change
 * it to any repos containing modified sources). JUST_THIS will commit only the
 * repository that owns the current project.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "commit", threadSafe = true)
@BaseMojoGoal("commit")
public class CommitMojo extends ScopedCheckoutsMojo
{
    /**
     * The commit message.
     */
    @Parameter(property = COMMIT_MESSAGE, required = true)
    private String commitMessage;

    /**
     * If true, do not call <code>git add -A</code> before committing - only
     * commit that which has been manually staged.
     */
    @Parameter(property = "cactus.commit.skip.add", defaultValue = "false")
    private boolean skipAdd;

    /**
     * If true, push after committing. If no remote branch of the same name as
     * the local branch exists, one will be created.
     */
    @Parameter(property = PUSH, defaultValue = "false")
    private boolean push;

    @Parameter(property = SKIP_CONFLICTS, defaultValue = "false")
    private boolean skipConflicts;

    @Parameter(property = STABLE_BRANCH, defaultValue = DEFAULT_STABLE_BRANCH)
    private String stableBranch;

    @Parameter(property = CREATE_AUTOMERGE_TAG, defaultValue = "false")
    private boolean createAutomergeTag;

    public CommitMojo()
    {
        super(RunPolicies.INITIAL);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.warn("No matched checkouts contain local modifications.");
            return;
        }

        GitCheckout root = tree.root();
        if (isIncludeRoot() && !checkouts.contains(root))
        {
            checkouts.add(root);
        }

        // Make sure nobody is in detached-head state, or we'll create a commit
        // that is not on any branch, and the changes will be un-findable if we
        // switch to a branch post-commit.
        for (GitCheckout checkout : checkouts)
        {
            if (checkout.isDetachedHead())
            {
                fail("Checkout is in detached-head state but has local changes. "
                        + "Switch to a branch before committing or you risk losing "
                        + "track of your commits.");
            }
        }
        boolean rootIsReallySubmoduleRoot = root.isSubmoduleRoot();

        Set<GitCheckout> needingPull = new HashSet<>();
        examineCheckoutsForConflicts(checkouts, log, needingPull, true,
                rootIsReallySubmoduleRoot
                ? root
                : null);

        performPulls(needingPull, log);

        CommitMessage msg = new CommitMessage(CommitMojo.class, commitMessage);
        Set<GitCheckout> toCommit = new LinkedHashSet<>();
        CharSequence nameList = nameList(checkouts, toCommit);
        if (toCommit.isEmpty())
        {
            log.warn("Nothing to commit among " + nameList);
            return;
        }
        addCommitMessageDetail(msg, toCommit);

        if (isVerbose())
        {
            log.info("Begin commit with message '" + commitMessage + "'");
        }
        for (GitCheckout at : toCommit)
        {
            log.info("add/commit " + at.loggingName());
            if (!isPretend())
            {
                if (!skipAdd)
                {
                    at.addAll();
                }
                at.commit(msg.toString());
                System.out.println("Committed " + at.loggingName());
            }
        }
        Set<GitCheckout> tagged = emptySet();
        if (createAutomergeTag)
        {
            tagged = AutomergeTagMojo.automergeTag(null, stableBranch, tree,
                    log, isPretend(), toCommit, false, this::automergeTag);
        }
        if (push)
        {
            // We need to do this again now that we have added a commit
            examineCheckoutsForConflicts(toCommit, log, needingPull, false,
                    rootIsReallySubmoduleRoot
                    ? root
                    : null);
            performPulls(needingPull, log);

            for (GitCheckout co : toCommit)
            {
                NeedPushResult np = co.needsPush();
                switch (np)
                {
                    case YES:
                        log.info("Push: " + co.loggingName());
                        ifNotPretending(co::push);
                        System.out.println("Pushed " + co.loggingName());
                        break;
                    case REMOTE_BRANCH_DOES_NOT_EXIST:
                        log.info("Push creating branch: " + co.loggingName());
                        ifNotPretending(co::pushCreatingBranch);
                        System.out.println(
                                "Pushed " + co.loggingName() + " creating remote branch "
                                + co.branch().get());
                        break;
                    default:
                        break;
                }
            }
            String tag = automergeTag().toString();
            for (GitCheckout co : tagged)
            {
                if (root.equals(co) && !isIncludeRoot()) {
                    continue;
                }
                log.info("Push tag " + tag);
                ifNotPretending(() -> co.pushTag(tag));
            }
        }
    }

    private StringBuilder nameList(List<GitCheckout> checkouts,
            Set<GitCheckout> toCommit)
    {
        StringBuilder nameList = new StringBuilder();
        for (GitCheckout co : checkouts)
        {
            if (co.hasUncommitedChanges())
            {
                toCommit.add(co);
            }
            if (nameList.length() > 0)
            {
                nameList.append(", ");
            }
            nameList.append(co.loggingName());
        }
        return nameList;
    }

    public void performPulls(Set<GitCheckout> needingPull, BuildLog log1)
    {
        for (GitCheckout co : needingPull)
        {
            if (safeToPullWithRebase(co))
            {
                log1.info("Pull with rebase: " + co.loggingName());
                ifNotPretending(co::pullWithRebase);
            }
            else
            {
                log1.info("Pull " + co.loggingName());
                ifNotPretending(co::pull);
            }
        }
        needingPull.clear();
    }

    private boolean safeToPullWithRebase(GitCheckout co)
    {
        String head = co.head();
        Branches containingCommit = co.branchesContainingCommit(head);
        // If no remote branch contains this commit, then it exists only locally,
        // so rebasing it (which will alter history and change its hash) is fine.
        //
        // If it *does* exist remotely, then pull --rebase *could* result in a
        // new history that would have to be force-pushed and then would break the
        // checkout of everyone else.
        return containingCommit.remoteBranches().isEmpty();
    }

    private Map<GitCheckout, Conflicts> checkForConflicts(
            Collection<? extends GitCheckout> checkouts, boolean useWorkingTree)
    {
        Map<GitCheckout, Conflicts> result = new TreeMap<>();
        for (GitCheckout co : checkouts)
        {
            Conflicts cf = useWorkingTree
                           ? co.canMergeWorkingTree()
                           : co.checkForConflicts();
            if (!cf.isEmpty())
            {
                System.out.println(
                        "Have conflicts for " + co.loggingName() + " wt " + useWorkingTree);
                System.out.println(cf);
            }
            result.put(co, cf);
        }
        return result;
    }

    public void examineCheckoutsForConflicts(
            Collection<GitCheckout> checkouts,
            BuildLog log1, Set<GitCheckout> needingPull,
            boolean useWorkingTree, GitCheckout rootCheckoutOrNull)
    {
        Map<GitCheckout, Conflicts> conflicts = new TreeMap<>();
        if (push)
        {
            Set<GitCheckout> notUpToDate = new LinkedHashSet<>();
            for (GitCheckout checkout : checkouts)
            {
                log1.info("Fetch all in " + checkout);
                ifNotPretending(checkout::fetchAll);
                if (checkout.needsPull())
                {
                    log1.info(
                            "Needs pull - will check for conflicts: "
                            + checkout.loggingName());
                    if (!checkout.equals(rootCheckoutOrNull))
                    {
                        notUpToDate.add(checkout);
                    }
                }
            }
            Map<GitCheckout, Conflicts> cfs = checkForConflicts(notUpToDate,
                    useWorkingTree);
            cfs.forEach((checkout, cflict) ->
            {
                System.out.println(cflict);
                if (cflict.hasHardConflicts())
                {
                    conflicts.put(checkout, cflict.filterHard());
                }
                else
                {
                    needingPull.add(checkout);
                    for (Conflict flict : cflict)
                    {
                        log1.info(
                                "Will need to pull remote changes for "
                                + checkout.loggingName()
                                + " to resolve\n" + flict);
                    }
                }
            });
        }
        if (!conflicts.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            conflicts.forEach((checkout, flicts) ->
            {
                sb.append('\n').append(checkout.loggingName()).append('\n')
                        .append(flicts);
            });
            if (skipConflicts)
            {
                sb.insert(0,
                        "Skipping " + conflicts.size() + " checkouts due to remote conflicts.");
                Set<GitCheckout> toRemove = conflicts.keySet();
                checkouts.removeAll(toRemove);
                needingPull.removeAll(toRemove);
                PrintMessageMojo.publishMessage(sb, session(), false);
            }
            else
            {
                sb.insert(0,
                        "Cannot proceed with push - remote has conflicting changes in " + conflicts
                                .size() + " checkouts. \n"
                        + "Re-run with -D.cactus.push=false (or unset) to generate a commit, and "
                        + "then manually pull and resolve conflicts.");
                fail(sb.toString());
            }
        }
    }
}
