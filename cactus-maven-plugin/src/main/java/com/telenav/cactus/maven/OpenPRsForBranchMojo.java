package com.telenav.cactus.maven;

import com.mastfrog.function.state.Int;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.BASE_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.DEFAULT_DEVELOPMENT_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.TARGET_BRANCH;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Open any PRs in the current branch or specified one in any matching checkout.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode",
            "CodeBlock2Expr"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresOnline = true,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "show-prs", threadSafe = true)
@BaseMojoGoal("show-prs")
public class OpenPRsForBranchMojo extends AbstractGithubMojo
{

    /**
     * If true, only open PRs that do not have conflicts and can be merged now.
     */
    @Parameter(property = "cactus.mergeable-only", defaultValue = "false")
    private boolean mergeableOnly;

    /**
     * The base branch the PR must be trying to merge to.
     */
    @Parameter(property = BASE_BRANCH, defaultValue = DEFAULT_DEVELOPMENT_BRANCH)
    String baseBranch;

    /**
     * The branch from which the pull request should have been created; if
     * unset, the current branch in the checkout is used.
     */
    @Parameter(property = TARGET_BRANCH)
    String targetBranch;

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        Map<GitCheckout, Branches.Branch> branches = checkoutsThatHaveBranch(
                myCheckout,
                targetBranch, checkouts, log, tree);
        if (branches.isEmpty())
        {
            log.warn("No branches found");
            return;
        }
        Int count = Int.create();
        branches.forEach((checkout, branch) ->
        {
            pullRequests(checkout, branch).forEach(pr ->
            {
                emitMessage("PR " + pr.number
                        + " of " + checkout.loggingName()
                        + " at " + pr.url);
                ifNotPretending(() -> open(pr.toURI()));
                count.increment();
            });
        });
        count.ifEqual(0, () -> emitMessage("No PRs found to open."));
    }

    private Collection<? extends MinimalPRItem> pullRequests(
            GitCheckout checkout, Branch branch)
    {
        if (mergeableOnly)
        {
            return openAndMergeablePullRequestsForBranch(
                    baseBranch,
                    branch.name(),
                    checkout
            );
        }
        else
        {
            return openPullRequestsForBranch(
                    baseBranch,
                    branch.name(),
                    checkout
            );
        }
    }

    @Override
    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        validateBranchName(baseBranch, false);
        validateBranchName(targetBranch, true);
    }

}
