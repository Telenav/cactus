package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.Branches;
import com.telenav.cactus.maven.git.Branches.Branch;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
        name = "attempt-merge", threadSafe = true)
public class MergeBuildMojo extends ScopedCheckoutsMojo
{

    /**
     * The stable branch to merge into.
     */
    @Parameter(property = "stable-branch", name = "stable-branch", defaultValue = "develop")
    private String stableBranch;

    /**
     * The branch to merge into the stable branch if the build succeeds.
     */
    @Parameter(property = "merge-branch", required = true)
    private String branchToMerge;

    /**
     * If true, merge and push to the remote stable branch on success.
     */
    @Parameter(property = "push", name = "push", defaultValue = "false")
    private boolean push;

    /**
     * If true, log what will be done.
     */
    @Parameter(property = "verbose", name = "verbose", defaultValue = "true")
    private boolean verbose;

    /**
     * If true, allow some checkouts not to have a branch with the stable-branch
     * name, and simply do not move them to a branch - this is primarily for
     * testing.
     */
    @Parameter(property = "ignore-no-stable-branch", defaultValue = "true")
    private boolean ignoreNoStableBranch;

    /**
     * If true, perform a <code>git fetch --all</code> on each repository before
     * checking for and attempting to create branches. Continuous builds will
     * want this always set to true, but it can slow things down for local
     * testing of changes to this mojo.
     */
    @Parameter(property = "fetch-first", defaultValue = "false")
    private boolean fetchFirst;

    private String tempBranch;

    public MergeBuildMojo()
    {
        super(true);
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        validateBranchName(branchToMerge, false);
        validateBranchName(stableBranch, false);
        tempBranch = branchToMerge + "_" + stableBranch + "_" + Long.toString(System.currentTimeMillis(), 36)
                + "_" + Long.toString(ThreadLocalRandom.current().nextLong(), 36);
        log.info("Temporary branch name " + tempBranch);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (fetchFirst)
        {
            fetchAll(checkouts, log.child("fetch"));
        }

        // Use LinkedHashSet - our checkout list is pre-sorted deepest-to-shallowest
        // and we want to maintain that sort order.
        Map<GitCheckout, Branch> branchesToCreateFrom = new LinkedHashMap<>();
        Map<GitCheckout, Branch> branchesToMerge = new LinkedHashMap<>();
        for (GitCheckout checkout : checkouts)
        {
            Branches branches = tree.branches(checkout);
            branches.find(stableBranch, false).ifPresent(br ->
            {
                branchesToCreateFrom.put(checkout, br);
            });
            branches.find(branchToMerge, true).ifPresentOrElse(br ->
            {
                branchesToMerge.put(checkout, br);
            }, () ->
            {
                branches.find(branchToMerge, false).ifPresent(remoteBranch ->
                {
                    branchesToMerge.put(checkout, remoteBranch);
                });
            });
        }
        if (!ignoreNoStableBranch && !branchesToCreateFrom.keySet().equals(new HashSet<>(checkouts)))
        {
            Set<GitCheckout> absent = new HashSet<>(checkouts);
            absent.removeAll(branchesToCreateFrom.keySet());
            fail("Not all checkouts have a stable branch named '" + stableBranch + "': " + absent);
        }
        if (branchesToMerge.isEmpty())
        {
            // Should this fail, or just end execution?
            fail("Did not find any branches named '" + branchToMerge + "' in " + checkouts);
        }
        if (verbose)
        {
            log.warn("Workflow for " + scope() + ":");
            branchesToCreateFrom.forEach((co, br) ->
            {
                Branch merge = branchesToMerge.get(co);
                if (merge != null)
                {
                    log.warn(co.name() + ":\tcreate " + tempBranch + " merging " + branchToMerge + " into " + stableBranch);
                }
            });
        }
        for (Map.Entry<GitCheckout, Branch> e : branchesToCreateFrom.entrySet())
        {
            Branch base = e.getValue();
            GitCheckout checkout = e.getKey();
            log.info("Create and switch to " + tempBranch + " in " + checkout.name());
            if (!isPretend())
            {
                checkout.createAndSwitchToBranch(tempBranch, Optional.of(base.toString()), isPretend());
            }
        }
        for (Map.Entry<GitCheckout, Branch> e : branchesToMerge.entrySet())
        {
            Branch target = e.getValue();
            GitCheckout checkout = e.getKey();
            log.info("Merge " + target + " into " + tempBranch + " for " + checkout.name());
            if (!isPretend())
            {
                checkout.merge(target.toString());
            }
        }
    }

    private void fetchAll(List<GitCheckout> checkouts, BuildLog log)
    {
        for (GitCheckout checkout : checkouts)
        {
            log.info("Fetch all in " + checkout.name());
            if (!isPretend())
            {
                checkout.fetchAll();
            }
        }
    }
}
