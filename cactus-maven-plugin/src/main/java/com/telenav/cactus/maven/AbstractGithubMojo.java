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
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.plugin.MojoFailureException;
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
        List<MinimalPRItem> result = new ArrayList<>(prListCache
                .computeIfAbsent(cacheKey,
                        k -> forCheckout.listPullRequests(this,
                                baseBranch, null)));

        for (Iterator<MinimalPRItem> it = result.iterator(); it.hasNext();)
        {
            MinimalPRItem pr = it.next();
            if (!pr.headRefName.equals(branchName)
                    && !pr.headRefName.contains(branchName))
            {
                it.remove();
            }
        }

        return result;
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

    /**
     * Fetch branches to query in a set of checkouts, based on the algorithm
     * used by <code>prBranchFor</code>.
     *
     * @param myCheckout The checkout maven was invoked in
     * @param checkouts A collection of checkouts to query
     * @param tree The project tree, which caches Branches instances for
     * checkouts, to avoid repeated, expensive lookups
     * @param targetBranch The target branch to query for, or null to use the
     * current branch of the target maven was invoked against
     * @return A map of branch to checkout for those checkouts that had a
     * matching branch
     */
    protected final Map<GitCheckout, Branches.Branch> prBranchesFor(
            GitCheckout myCheckout,
            Collection<? extends GitCheckout> checkouts, ProjectTree tree,
            String targetBranch)
    {
        Map<GitCheckout, Branches.Branch> result = new TreeMap<>();
        checkouts.forEach(checkout ->
        {
            prBranchFor(log(), myCheckout, checkout, tree, targetBranch, false)
                    .ifPresent(branch -> result.put(checkout, branch));
        });
        return result;
    }

    /**
     * Returns the branch an AbstractGithubMojo intends to target, if it exists,
     * using the passed target branch, or if null, the branch of the checkout in
     * which maven is being executed.
     *
     * @param log A logger
     * @param myCheckout The checkout of the project Maven was run against
     * @param targetCheckout A checkout to find a matching branch for, if one
     * exists
     * @param tree The project tree, which caches Branches objects to avoid
     * expensive repeated lookups
     * @param targetBranch The optional target branch provided to the mojo
     * @param failOnDetachedHead If true, the mojo should fail if it encounters
     * a checkout in detached-head state. If false, simply returns an empty
     * optional and logs a warning
     * @return A branch if one is matched
     */
    protected final Optional<Branches.Branch> prBranchFor(
            BuildLog log,
            GitCheckout myCheckout,
            GitCheckout targetCheckout,
            ProjectTree tree,
            String targetBranch,
            boolean failOnDetachedHead)
    {

        Branches branches = tree.branches(targetCheckout);
        // If the branch was explicitly passed (perhaps along with a list of
        // families, if we are in the project root), use that, and simply
        // only return something for the case that the checkout is already
        // on a branch with that name.
        //
        // Otherwise, what we want to look for is a branch with the same
        // name as the current branch of the checkout containing the project
        // maven was invoked against
        if (targetBranch != null)
        {
            // We were specifically told what branch to use - use it 
            // if present AND IF THE CHECKOUT IS CURRENTLY ON THAT BRANCH, or
            // skip the repository for the pull request otherwise
            return branches.currentBranch().flatMap(br ->
            {
                // Only return something if the explicitly specified target branch 
                // is the same branch as that of the checkout we are deciding
                // to include or not
                if (targetBranch.equals(br.name()))
                {
                    return of(br);
                }
                return empty();
            });
        }
        else
        {
            // Find out what branch the project we're RUNNING AGAINST is on,
            // and create a PR only for other matched checkouts which are on
            // a branch with the same name, so we create PRs from all branches
            // in the matched checkouts which are on a branch named feature/foo,
            // but do NOT create PRs for other checkouts which might contain
            // un-pushed commits, but are not on the branch we are using
            Branches targetProjectBranches = tree.branches(myCheckout);
            Optional<Branches.Branch> targetProjectsBranch
                    = targetProjectBranches.currentBranch();

            // The project we were run against is in detached-head state - we
            // have to fail here, as there is no way to track down a feature-branch
            // name to look for in other checkouts
            if (targetProjectsBranch.isEmpty())
            {
                String msg = "Target project " + coordinatesOf(project())
                        + " in " + project().getBasedir()
                        + " is not on a branch.  It needs to be to match "
                        + "same-named branches in other checkouts to "
                        + "decide what to create the pull request from.";
                if (failOnDetachedHead)
                {
                    // This will throw and get us out of here
                    fail(msg);
                }
                else
                {
                    log.warn(msg);
                    return empty();
                }
            }
            else
            {
                Optional<Branches.Branch> current = branches.currentBranch();
                if (current.isEmpty())
                {
                    // If the checkout we are queried about is in detached head state, don't
                    // use it, but log a warning.
                    log.warn(
                            "Ignoring " + targetCheckout.loggingName() + " for pull "
                            + "request - it is not on any branch.");
                    return current;
                }
                if (!current.get().name().equals(targetProjectsBranch.get()
                        .name()))
                {
                    // If the checkout we are queried about *is* on some branch, but
                    // not the right one, also ignore it and log that at level info:
                    log.info(
                            "Ignoring matched checkout " + targetCheckout
                                    .loggingName() + " for pull "
                            + "request - because we are matching the branch "
                            + targetProjectsBranch.get().name()
                            + " but it is on the branch " + current.get().name());
                    return empty();
                }
                else
                {
                    log.info("Will include " + targetCheckout.loggingName()
                            + " in the pull request set, on branch "
                            + targetProjectsBranch.get().name());
                }
                return current;
            }
            return empty();
        }
    }

    protected static Map<GitCheckout, Branches.Branch> checkoutsThatHaveBranch(
            GitCheckout myCheckout,
            String targetBranch,
            Collection<? extends GitCheckout> of, BuildLog log, ProjectTree tree)
            throws MojoFailureException
    {
        Map<GitCheckout, Branches.Branch> result = new TreeMap<>();
        String branchName = targetBranch == null
                            ? myCheckout.branch().orElseThrow(
                        () -> new MojoFailureException(
                                "No current branch for " + myCheckout
                                        .loggingName()))
                            : targetBranch;
        of.forEach(checkout
                -> tree.branches(checkout).find(branchName).ifPresent(
                        branch -> result.put(checkout, branch)));
        return result;
    }

    private static final class PullRequestListCacheKey
    {
        private final Path checkoutPath;
        private final String baseBranch;

        private PullRequestListCacheKey(String baseBranch, GitCheckout checkout,
                String branchName)
        {
            this.checkoutPath = notNull("checkout", checkout).checkoutRoot();
            this.baseBranch = baseBranch;
        }

        @Override
        public int hashCode()
        {
            return checkoutPath.hashCode()
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
                    && Objects.equals(baseBranch, key.baseBranch);
        }

        @Override
        public String toString()
        {
            return checkoutPath.getFileName()
                    + ":"
                    + (baseBranch == null
                       ? "<any>"
                       : baseBranch);
        }
    }
}
