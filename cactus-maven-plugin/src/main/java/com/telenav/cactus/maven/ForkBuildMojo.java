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
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PUSH;

/**
 * Performs the first steps of attempting to automatically merge a development
 * or feature branch into a stable branch - given a branch to merge, it will
 * create a new temporary branch from the stable branch, and merge the
 * development branch into it, failing if the merge creates conflicts.
 * <p>
 * If it succeeds, the build will proceed, and MergeToBranchMojo (which shares
 * data with this one) can be configured as a packaging step (at any step after
 * tests run, really) to merge the temporary branches into the stable branch.
 * </p><p>
 * The use case here is continuous builds which are set up to maintain a
 * "stable" branch, know about some set of "team" or feature branches, which
 * automatically update the stable branch from those branches if they are
 * mergeable and all tests pass, so developers working on other
 * branches/features have a stable source to merge from which incorporates the
 * work of their colleagues, and reduce the frequency/severity of "big bang"
 * merges.
 * </p><p>
 * To do that, you will want to set up your pom files with a profile that
 * executes this mojo on the validate phase (or some very early phase in the
 * build), and execute the MergeToBranchMojo afterwards (which will never run if
 * the build fails).
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
        name = "attempt-merge", threadSafe = true)
public class ForkBuildMojo extends ScopedCheckoutsMojo
{

    static final SharedDataKey<String> TEMP_BRANCH_KEY
            = SharedDataKey.of("tempBranch", String.class);

    static final SharedDataKey<GitCheckout[]> BRANCHED_REPOS_KEY
            = SharedDataKey.of(GitCheckout[].class);

    static final SharedDataKey<String> TARGET_BRANCH_KEY
            = SharedDataKey.of("mergeTo", String.class);

    @Inject
    SharedData sharedData;

    /**
     * The stable branch to merge into.
     */
    @Parameter(property = "cactus.stable-branch", defaultValue = "develop")
    private String stableBranch;

    /**
     * The branch to merge into the stable branch if the build succeeds.
     */
    @Parameter(property = "cactus.merge-branch", required = true)
    private String mergeBranch;

    /**
     * If true, merge and push to the remote stable branch on success.
     */
    @Parameter(property = PUSH, defaultValue = "false")
    private boolean push;

    /**
     * If true, allow some checkouts not to have a branch with the stable-branch
     * name, and simply do not move them to a branch - this is primarily for
     * testing.
     */
    @Parameter(property = "cactus.ignore-no-stable-branch",
            defaultValue = "true")
    private boolean ignoreNoStableBranch;

    /**
     * If true, perform a <code>git fetch --all</code> on each repository before
     * checking for and attempting to create branches. Continuous builds will
     * want this always set to true, but it can slow things down for local
     * testing of changes to this mojo.
     */
    @Parameter(property = "cactus.fetch-first", defaultValue = "false")
    private boolean fetchFirst;

    private String tempBranch;

    public ForkBuildMojo()
    {
        // This needs to run before the build, so the build builds the right
        // thing
        super(true);
        new Exception("Create fork build mojo").printStackTrace(System.out);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
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
            branches.find(mergeBranch, true).ifPresentOrElse(br ->
            {
                branchesToMerge.put(checkout, br);
            }, () ->
            {
                branches.find(mergeBranch, false).ifPresent(remoteBranch ->
                {
                    branchesToMerge.put(checkout, remoteBranch);
                });
            });
        }
        if (!ignoreNoStableBranch && !branchesToCreateFrom.keySet().equals(
                new HashSet<>(checkouts)))
        {
            Set<GitCheckout> absent = new HashSet<>(checkouts);
            absent.removeAll(branchesToCreateFrom.keySet());
            fail("Not all checkouts have a stable branch named '" + stableBranch + "': " + absent);
        }
        if (branchesToMerge.isEmpty())
        {
            // Should this fail, or just end execution?
            fail("Did not find any branches named '" + mergeBranch + "' in " + checkouts);
        }
        ifVerbose(() ->
        {
            log.warn("Workflow for " + scope() + ":");
            branchesToCreateFrom.forEach((co, br) ->
            {
                Branch merge = branchesToMerge.get(co);
                if (merge != null)
                {
                    log.warn(
                            co.name() + ":\tcreate " + tempBranch + " merging " + mergeBranch + " into " + stableBranch);
                }
            });
        });
        for (Map.Entry<GitCheckout, Branch> e : branchesToCreateFrom.entrySet())
        {
            Branch base = e.getValue();
            GitCheckout checkout = e.getKey();
            log.info("Create and switch to " + tempBranch + " in " + checkout
                    .name());
            boolean success = checkout.createAndSwitchToBranch(tempBranch,
                    Optional.of(base.trackingName()), isPretend());
            if (!success)
            {
                fail("Failed to create and switch to " + tempBranch + " using " + base);
            }
        }
        List<GitCheckout> successfullyMerged = new ArrayList<>();
        for (Map.Entry<GitCheckout, Branch> e : branchesToMerge.entrySet())
        {
            Branch target = e.getValue();
            GitCheckout checkout = e.getKey();
            log.info(
                    "Merge " + target + " into " + tempBranch + " for " + checkout
                            .name());
            if (!isPretend())
            {
                if (checkout.merge(target.trackingName()))
                {
                    successfullyMerged.add(checkout);
                }
                else
                {
                    //                    fail("Failed to merge " + target + " into " + tempBranch);
                    log
                            .warn("Failed to merge " + target + " into " + tempBranch);
                }
            }
        }
        sharedData.put(BRANCHED_REPOS_KEY, successfullyMerged.toArray(
                GitCheckout[]::new));
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        validateBranchName(mergeBranch, false);
        validateBranchName(stableBranch, false);
        tempBranch = mergeBranch + "_" + stableBranch + "_" + Long.toString(
                System.currentTimeMillis(), 36)
                + "_" + Long
                        .toString(ThreadLocalRandom.current().nextLong(), 36);
        sharedData.put(TEMP_BRANCH_KEY, tempBranch);
        sharedData.put(TARGET_BRANCH_KEY, stableBranch);
        log.info("Temporary branch name " + tempBranch);
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
