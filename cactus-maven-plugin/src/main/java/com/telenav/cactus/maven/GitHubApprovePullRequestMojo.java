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

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.trigger.RunPolicies.LAST;
import static java.util.Collections.emptyMap;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Approves all pull requests for a given branch.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
                "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "git-approve-pull-request", threadSafe = true)
@BaseMojoGoal("git-approve-pull-request")
public class GitHubApprovePullRequestMojo extends AbstractGithubMojo
{
    /**
     * Text body to associate with the approval (optional)
     */
    @Parameter(property = "cactus.pr.body")
    private String body;

    /**
     * The branch we intend to approve
     */
    @Parameter(property = "cactus.pr.branch-to-approve")
    private String branchToApprove;

    public GitHubApprovePullRequestMojo()
    {
        super(LAST);
    }

    @Override
    protected void execute(BuildLog log,
                           MavenProject project,
                           GitCheckout myCheckout,
                           ProjectTree tree,
                           List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.info("No checkouts matched.");
            return;
        }

        var pullRequests = findInitialPullRequest(log, branchToApprove, myCheckout, checkouts);
        if (pullRequests.isEmpty())
        {
            fail("No approve-able pull requests found with the head branch '" + branchToApprove + "'");
        }

        collectPullRequestsToApprove(log, branchToApprove, checkouts, pullRequests);

        String logPrefix = isPretend()
                ? "(pretend) "
                : "";

        log.info("Have " + pullRequests.size() + " pull requests to approve");

        pullRequests.forEach((checkout, pullRequest) ->
        {
            log.info(logPrefix + "Approve pull request " + pullRequest.number + " for " + checkout.loggingName()
                    + " on branch " + branchToApprove + " to " + pullRequest.baseRefName + ": '" + pullRequest.title + "'");
            if (!isPretend())
            {
                checkout.approvePullRequest(this, pullRequest.headRefName, Optional.of(body));
            }
        });
    }

    @Override
    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
    {
        validateBranchName(branchToApprove, true);
    }

    @SuppressWarnings("SpellCheckingInspection")
    List<MinimalPRItem> openAndMergeablePullRequestsForBranch(String branchName, GitCheckout forCheckout)
    {
        return openAndMergeablePullRequestsForBranch(null, branchName, forCheckout);
    }

    List<MinimalPRItem> pullRequestsForBranch(String branchName,
                                              GitCheckout forCheckout)
    {
        return pullRequestsForBranch(null, branchName, forCheckout);
    }

    private void collectPullRequestsToApprove(BuildLog log,
                                              String branchName,
                                              List<GitCheckout> checkouts,
                                              Map<? super GitCheckout, ? super MinimalPRItem> into)
    {
        // Collect the remaining pull request associated with the branch
        for (var checkout : checkouts)
        {
            // don't make a call over the wire twice if we already know the answer
            if (!into.containsKey(checkout))
            {
                leadPullRequestForBranch(branchName, checkout)
                        .ifPresent(item -> into.put(checkout, item));
            }
        }
    }

    private Map<GitCheckout, MinimalPRItem> findInitialPullRequest(BuildLog log,
                                                                   String branchName,
                                                                   GitCheckout myCheckout,
                                                                   List<GitCheckout> checkouts)
    {
        // Try our own checkout preferentially
        Optional<MinimalPRItem> item = leadPullRequestForBranch(branchName, myCheckout);

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
        return emptyMap();
    }

    private Optional<MinimalPRItem> leadPullRequestForBranch(String branchName, GitCheckout forCheckout)
    {
        return leadPullRequestForBranch(null, branchName, forCheckout);
    }
}
