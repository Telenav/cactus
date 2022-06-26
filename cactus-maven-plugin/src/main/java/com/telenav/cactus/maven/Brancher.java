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
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.util.EnumMatcher;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Branch creation logic shared between CheckoutMojo and CheckoutMojo.
 */
class Brancher
{

    /**
     * How the brancher should behave if the requested branch does not exist locally or remotely.
     */
    public enum NonexistentBranchBehavior
    {
        USE_DEVELOPMENT_BRANCH,
        CREATE_BRANCH,
        FAIL;

        public static Optional<NonexistentBranchBehavior> find(String what)
        {
            return EnumMatcher.enumMatcher(NonexistentBranchBehavior.class)
                    .match(what);
        }
    }

    public interface BranchCreationPolicy
    {
    }

    private final String branch;

    private final String failoverBaseBranch;

    private final BuildLog log;

    private final BaseMojo mojo;

    private final boolean pretend;

    private final NonexistentBranchBehavior onNoBranch;

    public Brancher(String branch, String failoverBaseBranch, BuildLog log,
                    BaseMojo mojo, boolean pretend, NonexistentBranchBehavior onNoBranch)
    {
        this.branch = branch == null
                ? failoverBaseBranch
                : branch;
        this.failoverBaseBranch = failoverBaseBranch;
        this.log = log.child("brancher");
        this.mojo = mojo;
        this.pretend = pretend;
        this.onNoBranch = onNoBranch;
    }

    List<GitCheckout> checkoutsNotAlreadyOnBranch(List<GitCheckout> checkouts)
    {
        return checkouts.stream().filter(co -> !co.isBranch(branch)).collect(
                Collectors.toCollection(ArrayList::new));
    }

    void fetchAll(Collection<? extends GitCheckout> checkouts, BuildLog log)
            throws MojoExecutionException
    {
        for (GitCheckout checkout : checkouts)
        {
            log.info("Fetch all in " + checkout.name());
            if (!pretend)
            {
                if (!checkout.fetchAll())
                {
                    mojo.fail("Fetch all failed in " + checkout);
                }
            }
        }
    }

    Set<GitCheckout> needingBranch(ProjectTree tree,
                                   Collection<? extends GitCheckout> toProcess,
                                   BuildLog log) throws MojoExecutionException
    {
        Set<GitCheckout> needingBranchCreation = new HashSet<>();
        for (GitCheckout co : toProcess)
        {
            Branches branches = tree.branches(co);
            Optional<Branches.Branch> localBranch = branches.find(branch, true);
            if (!localBranch.isPresent())
            {
                Optional<Branches.Branch> remoteBranch = branches.find(branch,
                        false);
                if (remoteBranch.isPresent())
                {
                    if (co.isDirty())
                    {
                        mojo.fail(
                                "Would create a new branch '" + branch + " to track '" + remoteBranch
                                        .get() + " but there are local modifications in " + co + " that will cause branch creation to fail." + "Stash or commit your changes first.");
                    }
                    log.info(
                            "Will create new local tracking branch for '" + remoteBranch
                                    .get() + "'");
                    needingBranchCreation.add(co);
                }
                else
                {
                    boolean haveFailover = failoverBaseBranch == null || failoverBaseBranch
                            .isEmpty();
                    switch (this.onNoBranch)
                    {
                        case CREATE_BRANCH:
                    }
                    if (failoverBaseBranch == null || failoverBaseBranch
                            .isEmpty())
                    {
                        mojo.fail(
                                "No local branch named '" + branch + "' exists, and there is no remote tracking branch " + "with the same name to branch off of.");
                    }
                }
            }
        }
        return needingBranchCreation;
    }

    void performCheckouts(List<GitCheckout> toProcess, BuildLog log,
                          Set<GitCheckout> createLocalBranch) throws MojoExecutionException
    {
        for (GitCheckout checkout : toProcess)
        {
            if (createLocalBranch.contains(checkout))
            {
                log.info(
                        "Checkout " + checkout.name() + '[' + branch + "] creating local branch for existing remote branch.");
                checkout.createAndSwitchToBranch(branch, Optional.empty(),
                        pretend);
            }
            else
            {
                log.info("Checkout " + checkout.name() + '[' + branch + ']');
                if (!pretend)
                {
                    checkout.switchToBranch(branch);
                }
            }
        }
    }

    boolean updateBranches(List<GitCheckout> checkouts, BuildLog buildLog,
                           ProjectTree tree) throws MojoExecutionException
    {
        List<GitCheckout> toProcess = checkoutsNotAlreadyOnBranch(checkouts);
        if (toProcess.isEmpty())
        {
            buildLog.info(
                    "All matched checkouts are already on branch '" + branch + '\'');
            return false;
        }
        updateRemoteHeads(toProcess, tree, buildLog.child("updateHeads"));
        Set<GitCheckout> createLocalBranch = needingBranch(tree, toProcess,
                buildLog.child("branchAbsent"));
        fetchAll(toProcess, buildLog.child("fetchAll"));
        performCheckouts(toProcess, buildLog.child("checkout"),
                createLocalBranch);
        return true;
    }

    void updateRemoteHeads(Collection<? extends GitCheckout> checkouts,
                           ProjectTree tree, BuildLog log)
    {
        checkouts.forEach(co ->
        {
            log.info("Update remote heads for " + co.name());
            if (!pretend)
            {
                co.updateRemoteHeads();
            }
        });
        tree.invalidateCache();
    }
}
