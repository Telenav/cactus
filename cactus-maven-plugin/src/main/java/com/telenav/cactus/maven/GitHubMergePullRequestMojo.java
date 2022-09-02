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
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.github.MergePullRequestOptions;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.github.MergePullRequestOptions.MERGE;
import static com.telenav.cactus.github.MergePullRequestOptions.REBASE;
import static com.telenav.cactus.github.MergePullRequestOptions.SQUASH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.TARGET_BRANCH;
import static com.telenav.cactus.maven.trigger.RunPolicies.LAST;
import static java.util.Collections.emptyMap;
import static java.util.EnumSet.noneOf;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Merges pull requests in all matching submodules, where the pull request's
 * head branch matches the target branch - which is either passed explicitly, or
 * is the branch the checkout owning the project Maven was invoked against is
 * on.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "git-merge-pull-request", threadSafe = true)
@BaseMojoGoal("git-merge-pull-request")
public class GitHubMergePullRequestMojo extends AbstractGithubMojo
{
    /**
     * The name of the PR branch to merge. If set explicitly, it may be a branch
     * of any of the matched checkouts. If unset, the branch of the checkout
     * owning the project we are invoked against is used, and there <i>must</i>
     * be a exactly one pull request extant for that branch which is open and
     * merge-able.
     */
    @Parameter(property = TARGET_BRANCH)
    private String targetBranch;

    /**
     * If true, pass <code>--auto</code> to `gh pr merge`.
     */
    @Parameter(property = "cactus.pr.auto", defaultValue = "false")
    private boolean auto;

    /**
     * If true, pass <code>--delete-branch</code> to `gh pr merge`.
     */
    @Parameter(property = "cactus.pr.delete-branch", defaultValue = "false")
    private boolean deleteBranch;

    /**
     * If true, pass <code>--merge</code> to `gh pr merge`. Mutually exclusive
     * with the rebase option.
     */
    @Parameter(property = "cactus.pr.merge", defaultValue = "true")
    private boolean merge;

    /**
     * If true, pass <code>--squash</code> to `gh pr merge`,
     */
    @Parameter(property = "cactus.pr.squash", defaultValue = "false")
    private boolean squash;

    /**
     * If true, pass <code>--rebase</code> to `gh pr merge`. Mutually exclusive
     * with the merge option.
     */
    @Parameter(property = "cactus.pr.rebase", defaultValue = "false")
    private boolean rebase;

    /**
     * If true, pass <code>--auto</code> to `gh pr merge`.
     */
    @Parameter(property = "cactus.pr.admin", defaultValue = "false")
    private boolean admin;

    /**
     * The base branch we intend the PR to be merged to - this is not passed to
     * the client, but to weed out checkouts which are on it and therefore not
     * likely to be part of the stuff to merge. The default is `develop`.
     */
    @Parameter(property = "cactus.base-branch", defaultValue = "develop")
    private final String baseBranch = "develop";

    public GitHubMergePullRequestMojo()
    {
        super(LAST);
    }

    @Override
    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
    {
        validateBranchName(targetBranch, true);
        validateBranchName(baseBranch, false);
        Set<MergePullRequestOptions> opts = options();
        Set<MergePullRequestOptions> cannotBeCombined
                = EnumSet.of(MERGE, SQUASH, REBASE);
        cannotBeCombined.retainAll(opts);
        if (cannotBeCombined.size() > 1)
        {
            fail("Only one of MERGE, REBASE and SQUASH may be set to true, but "
                    + "have the options " + opts);
        }
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.info("No checkouts matched.");
            return;
        }
        String branch = targetBranch(myCheckout, tree);
        if (branch.equals(baseBranch))
        {
            fail("The target branch and the base branch are both '" + branch
                    + "'.  There will not be any PRs from a branch to itself.");
        }

        Map<GitCheckout, MinimalPRItem> prForCheckout = findInitialPR(log,
                branch, myCheckout, checkouts);
        if (prForCheckout.isEmpty())
        {
            fail("No open and merge-able PRs found with the head branch '" + branch + "'");
        }

        collectPrsToMerge(log, branch, checkouts, prForCheckout);

        Set<MergePullRequestOptions> options = options();

        String logPrefix = isPretend()
                           ? "(pretend) "
                           : "";

        log.info("Have " + prForCheckout.size() + " PRs to merge");

        prForCheckout.forEach((checkout, pr) ->
        {
            log.info(logPrefix + "Merge PR " + pr.number + " for " + checkout
                    .loggingName()
                    + " on branch " + branch + " to " + pr.baseRefName + ": '" + pr.title + "'");
            if (!isPretend())
            {
                checkout.mergePullRequest(this, pr.headRefName, options);
            }
        });
    }

    private void collectPrsToMerge(BuildLog log, String branchName,
            List<GitCheckout> checkouts,
            Map<? super GitCheckout, ? super MinimalPRItem> into)
    {
        // Collect the remainder of PRs associated with the branch name in
        // question
        for (GitCheckout co : checkouts)
        {
            // don't make a call over the wire twice if we already know the answer
            if (!into.containsKey(co))
            {
                leadPullRequestForBranch(branchName, co)
                        .ifPresent(item -> into.put(co, item));
            }
        }
    }

    private Map<GitCheckout, MinimalPRItem> findInitialPR(BuildLog log,
            String branchName,
            GitCheckout myCheckout,
            List<GitCheckout> checkouts)
    {
        // Finds the first PR associated with the branch being targeted
        // (either explicitly set, or the branch of the checkout of the invoking
        // project).  If it is not explicitly set, one such must exist ofr the
        // invoking project.  If it was, then it can be in any project, and the
        // invoking project may not even be involved in the pull request.
        //
        // We return this is a map we can then continue populating to minimize
        // expensive over-the-wire calls, so we do not list PRs more than once
        // per checkout.
        if (targetBranch == null)
        {
            // We were not passed a branch name, so we *must* find a PR using the
            // current branch of myCheckout as its head
            Map<GitCheckout, MinimalPRItem> result = new LinkedHashMap<>(1);
            leadPullRequestForBranch(branchName, myCheckout).ifPresent(
                    pr -> result.put(
                            myCheckout, pr));
            return result;
        }
        else
        {
            // Try our own checkout preferentially
            Optional<MinimalPRItem> item = leadPullRequestForBranch(branchName,
                    myCheckout);
            // Okay, we may just be in the root project but looking for the
            // named branch in other projects.
            if (item.isEmpty())
            {
                for (GitCheckout test : checkouts)
                {
                    if (!test.equals(myCheckout))
                    {
                        item = leadPullRequestForBranch(branchName, test);
                        if (item.isPresent())
                        {
                            Map<GitCheckout, MinimalPRItem> result = new LinkedHashMap<>(
                                    1);
                            result.put(test, item.get());
                            return result;
                        }
                    }
                }
            }
        }
        return emptyMap();
    }

    private String targetBranch(GitCheckout myCheckout, ProjectTree tree) throws MojoFailureException
    {
        if (targetBranch != null && !targetBranch.isBlank())
        {
            // If explicitly set, use the value set
            return targetBranch;
        }
        else
        {
            // If not explicitly set, we're looking for PRs whose head branch
            // has the same name as the checkout we were invoked in
            Branches myBranches = tree.branches(myCheckout);
            return myBranches.currentBranch().orElseThrow(
                    () -> new MojoFailureException(myCheckout.loggingName()
                            + " is not on a branch - pass cactus.target-branch "
                            + "or check out a branch and retry."))
                    .name();
        }
    }

    private Optional<MinimalPRItem> leadPullRequestForBranch(String branchName,
            GitCheckout forCheckout)
    {
        return leadPullRequestForBranch(baseBranch, branchName, forCheckout);
    }

    List<MinimalPRItem> pullRequestsForBranch(String branchName,
            GitCheckout forCheckout)
    {
        return pullRequestsForBranch(baseBranch, branchName, forCheckout);
    }

    @SuppressWarnings("SpellCheckingInspection")
    List<MinimalPRItem> openAndMergeablePullRequestsForBranch(String branchName,
                                                              GitCheckout forCheckout)
    {
        return openAndMergeablePullRequestsForBranch(baseBranch, branchName,
                forCheckout);
    }

    private Set<MergePullRequestOptions> options()
    {
        // Get the set of options based on the boolean properties of this mojo
        Set<MergePullRequestOptions> result
                = noneOf(MergePullRequestOptions.class);
        boolean[] items =
        {
            auto, deleteBranch, merge, squash, rebase, admin
        };

        MergePullRequestOptions[] all = MergePullRequestOptions.values();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i])
            {
                result.add(all[i]);
            }
        }
        return result;
    }
}
