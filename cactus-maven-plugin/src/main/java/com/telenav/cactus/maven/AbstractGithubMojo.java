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

import com.mastfrog.function.throwing.io.IOSupplier;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.lang.System.getenv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.readString;
import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * Base class for mojos which use the GitHub CLI which may require supplying an
 * authentication token.
 *
 * @author Tim Boudreau
 */
abstract class AbstractGithubMojo extends ScopedCheckoutsMojo
        implements IOSupplier<String>
{
    public static final String GITHUB_CLI_PAT_ENV_VAR = "CACTUS_GITHUB_PERSONAL_ACCESS_TOKEN";
    public static final String GITHUB_CLI_PAT_FILE_ENV_VAR = "CACTUS_GITHUB_PERSONAL_ACCESS_TOKEN_FILE";

    /**
     * Github authentication token to use with the github cli client. If not
     * present, the GITHUB_PAT environment variable must be set to a valid
     * github personal access token, or the GITHUB_PAT_FILE environment variable
     * must be set to an extant file that contains the personal access token and
     * nothing else.
     * <p>
     * It is not <i>required</i> that an access token be available, but if none
     * is, and authentication is required for a github operation, the build will
     * fail at that point.
     * </p>
     */
    @Parameter(property = "cactus.github-personal-access-token",
            required = false)
    private String authenticationToken;

    // Cache the results of wire calls
    private final Map<PullRequestListCacheKey, List<MinimalPRItem>> prListCache
            = new ConcurrentHashMap<>();

    protected AbstractGithubMojo()
    {
    }

    protected AbstractGithubMojo(boolean runFirst)
    {
        super(runFirst);
    }

    protected AbstractGithubMojo(RunPolicy policy)
    {
        super(policy);
    }

    @Override
    protected final void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        if (authenticationToken == null || authenticationToken.isBlank())
        {
            String result = getenv(GITHUB_CLI_PAT_ENV_VAR);
            if (result == null)
            {
                result = getenv(GITHUB_CLI_PAT_FILE_ENV_VAR);
            }
            if (result == null)
            {
                log.warn(
                        "-Dcactus.github-personal-access-token not passed, and neither "
                        + GITHUB_CLI_PAT_ENV_VAR + " nor " + GITHUB_CLI_PAT_FILE_ENV_VAR
                        + " are set in the environment.  If github calls need "
                        + "authentication, they will fail.");
            }
        }
        onValidateGithubParameters(log, project);
    }

    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        // do nothing - for subclasses
    }

    @Override
    public final String get() throws IOException
    {
        if (authenticationToken == null || authenticationToken.isBlank())
        {
            return getTokenFromEnvironment();
        }
        return authenticationToken;
    }

    private String getTokenFromEnvironment() throws IOException
    {
        // First try the environment variable that holds the PAT text
        String result = getenv(GITHUB_CLI_PAT_ENV_VAR);
        if (result == null)
        {
            // If not present, look for the file path env var
            String filePath = getenv(GITHUB_CLI_PAT_FILE_ENV_VAR);
            if (filePath != null)
            {
                Path file = Paths.get(filePath.trim());
                if (exists(file))
                {
                    return readString(file, UTF_8).trim();
                }
                else
                {
                    // If it is set but does not exist, the operator needs
                    // to fix that - fail hard.
                    throw new IOException(GITHUB_CLI_PAT_FILE_ENV_VAR
                            + " is set to " + filePath + " but it does not exist.");
                }
            }
        }
        else
        {
            // Ensure if it was embedded in XML that we trim it down to
            // what's needed
            result = result.trim();
        }
        return result;
    }

    /**
     * Get all pull requests using the passed branches.
     *
     * @param baseBranch The base branch the PR wants to be merged to - may be
     * null to match PRs targeting any branch
     * @param branchName The name of the PR's origin branch
     * @param forCheckout The checkout / repository it belongs to
     * @return A list of pull requests
     */
    protected final List<MinimalPRItem> pullRequestsForBranch(String baseBranch,
            String branchName, GitCheckout forCheckout)
    {
        // We may trawl through pull requests multiple times, so ensure we only
        // do one wire call per
        PullRequestListCacheKey cacheKey = new PullRequestListCacheKey(
                baseBranch,
                forCheckout,
                branchName);
        // Use a cached list if present:
        List<MinimalPRItem> result = prListCache.computeIfAbsent(cacheKey,
                k -> forCheckout.listPullRequests(this,
                        baseBranch, branchName, null));

        // The caller prune unusable items, so return a copy
        return new ArrayList<>(result);
    }

    /**
     * In the case that the set of pull requests has been programmatically
     * changed, dump any cached `gh pr list` results.
     */
    protected final void clearPRCache()
    {
        prListCache.clear();
    }

    /**
     * Like <code>pullRequestsForBranch</code> but filters out any pull requests
     * that whose state is not <code>OPEN</code> or whose mergeable status is
     * not <code>MERGEABLE</code>.
     *
     * @param baseBranch The base branch (may be null to search any)
     * @param branchName The terget branch
     * @param forCheckout The checkout in question
     * @return A list of pull requests
     */
    protected final List<MinimalPRItem> openAndMergeablePullRequestsForBranch(
            String baseBranch, String branchName, GitCheckout forCheckout)
    {
        return filterNonOpenOrNotMergeable(log(), forCheckout,
                pullRequestsForBranch(baseBranch, branchName, forCheckout));
    }

    protected final List<MinimalPRItem> openPullRequestsForBranch(
            String baseBranch, String branchName, GitCheckout forCheckout)
    {
        return filterNonOpen(log(), forCheckout,
                pullRequestsForBranch(baseBranch, branchName, forCheckout));
    }

    /**
     * Get the "lead" pull request for a branch - if there are zero pull
     * requests for this branch combination, returns empty; if there is one,
     * returns that; if more than once, the result is ambiguous and we have no
     * way to disambiguate which pull request the user might want to operate on,
     * so fail.
     *
     * @param baseBranch The base branch the PR wants to merge to (optional)
     * @param branchName The branch the PR is on
     * @param forCheckout The checkout in question
     * @return A record of a PR if one exists
     */
    protected final Optional<MinimalPRItem> leadPullRequestForBranch(
            String baseBranch,
            String branchName,
            GitCheckout forCheckout)
    {
        // Find a PR for the given branch name in the given checkout
        List<MinimalPRItem> items = openAndMergeablePullRequestsForBranch(
                baseBranch, branchName, forCheckout);
        switch (items.size())
        {
            case 0:
                // Okay, nothing here - that may be fine
                return empty();
            case 1:
                // Exactly one PR associated with this branch - the ideal,
                // unambiguous case
                return of(items.get(0));
            default:
                // We do NOT pick a PR at random to merge and hope for the best.
                return fail(
                        "Ambiguous PRs - more than one PR on " + branchName + " in "
                        + forCheckout.loggingName() + ": " + items);
        }
    }

    private List<MinimalPRItem> filterNonOpenOrNotMergeable(BuildLog log,
            GitCheckout in, List<MinimalPRItem> items)
    {
        // If the merge would fail, prune it out
        for (Iterator<MinimalPRItem> it = items.iterator(); it.hasNext();)
        {
            MinimalPRItem i = it.next();
            if (!i.isOpen() || !i.isMergeable())
            {
                log.warn(
                        "Filter closed or not-mergeable from candidates for " + in
                                .loggingName() + ": " + i);
                it.remove();
            }
        }
        return items;
    }

    private List<MinimalPRItem> filterNonOpen(BuildLog log,
            GitCheckout in, List<MinimalPRItem> items)
    {
        // If the merge would fail, prune it out
        for (Iterator<MinimalPRItem> it = items.iterator(); it.hasNext();)
        {
            MinimalPRItem i = it.next();
            if (!i.isOpen())
            {
                log.warn(
                        "Filter closed or not-mergeable from candidates for " + in
                                .loggingName() + ": " + i);
                it.remove();
            }
        }
        return items;
    }

    private static final class PullRequestListCacheKey
    {
        private final Path checkoutPath;
        private final String targetBranch;
        private final String baseBranch;

        private PullRequestListCacheKey(String baseBranch, GitCheckout checkout,
                String branchName)
        {
            this.checkoutPath = notNull("checkout", checkout).checkoutRoot();
            this.targetBranch = branchName;
            this.baseBranch = baseBranch;
        }

        @Override
        public int hashCode()
        {
            return checkoutPath.hashCode()
                    + (71 * Objects.hashCode(targetBranch))
                    + (263 * Objects.hashCode(baseBranch));
        }

        @Override
        public boolean equals(Object o)
        {
            if (o == this)
            {
                return true;
            }
            else
                if (o == null || o.getClass() != PullRequestListCacheKey.class)
                {
                    return false;
                }
            PullRequestListCacheKey key = (PullRequestListCacheKey) o;
            return key.checkoutPath.toAbsolutePath().equals(checkoutPath
                    .toAbsolutePath())
                    && Objects.equals(baseBranch, key.baseBranch)
                    && Objects.equals(targetBranch, key.targetBranch);
        }

        @Override
        public String toString()
        {
            return checkoutPath.getFileName()
                    + ":"
                    + (targetBranch == null
                       ? "<any>"
                       : targetBranch)
                    + "->" + (baseBranch == null
                              ? "<any>"
                              : baseBranch);
        }
    }
}
