package com.telenav.cactus.maven;

import com.google.common.base.Strings;
import com.telenav.cactus.maven.CheckoutMojo.Brancher.NonexistentBranchBehavior;
import com.telenav.cactus.maven.git.Branches;
import com.telenav.cactus.maven.git.Branches.Branch;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import static com.telenav.cactus.maven.util.EnumMatcher.enumMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
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
        name = "checkout", threadSafe = true)
public class CheckoutMojo extends ScopedCheckoutsMojo
{

    @Parameter(property = "branch", required = true)
    private String branch;

    @Parameter(property = "default-base-branch", required = true)
    private String failoverBaseBranch;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        if (Strings.isNullOrEmpty(branch))
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        validateBranchName(branch, false);
        validateBranchName(failoverBaseBranch, true);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.info("No checkouts matched.");
            return;
        }
        new Brancher(branch, failoverBaseBranch, log, this, isPretend(), NonexistentBranchBehavior.FAIL)
                .updateBranches(checkouts, log, tree);
    }

    /**
     * Branch creation logic shared between CheckoutMojo and
     * DevelopmentPrepMojo.
     */
    static class Brancher
    {

        private final String branch;
        private final String failoverBaseBranch;
        private final BuildLog log;
        private final BaseMojo mojo;
        private final boolean pretend;
        private final NonexistentBranchBehavior onNoBranch;

        public Brancher(String branch, String failoverBaseBranch, BuildLog log,
                BaseMojo mojo, boolean pretend,
                NonexistentBranchBehavior onNoBranch)
        {
            this.branch = branch == null ? failoverBaseBranch : branch;
            this.failoverBaseBranch = failoverBaseBranch;
            this.log = log.child("brancher");
            this.mojo = mojo;
            this.pretend = pretend;
            this.onNoBranch = onNoBranch;
        }

        /**
         * How the brancher should behave if the requested branch does not exist
         * locally or remotely.
         */
        public enum NonexistentBranchBehavior
        {
            USE_DEVELOPMENT_BRANCH,
            CREATE_BRANCH,
            FAIL;

            public static Optional<NonexistentBranchBehavior> find(String what)
            {
                return enumMatcher(NonexistentBranchBehavior.class).match(what);
            }
        }

        List<GitCheckout> checkoutsNotAlreadyOnBranch(List<GitCheckout> checkouts)
        {
            return checkouts.stream().filter(co -> !co.isBranch(branch))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        Set<GitCheckout> needingBranch(ProjectTree tree,
                Collection<? extends GitCheckout> toProcess, BuildLog log) throws MojoExecutionException
        {
            Set<GitCheckout> needingBranchCreation = new HashSet<>();
            for (GitCheckout co : toProcess)
            {
                Branches branches = tree.branches(co);
                Optional<Branch> localBranch = branches.find(branch, true);
                if (!localBranch.isPresent())
                {
                    Optional<Branch> remoteBranch = branches.find(branch, false);
                    if (remoteBranch.isPresent())
                    {
                        if (co.isDirty())
                        {
                            mojo.fail("Would create a new branch '" + branch
                                    + " to track '" + remoteBranch.get()
                                    + " but there are local modifications in " + co
                                    + " that will cause branch creation to fail."
                                    + "Stash or commit your changes first.");
                        }
                        log.info("Will create new local tracking branch for '"
                                + remoteBranch.get() + "'");
                        needingBranchCreation.add(co);
                    } else
                    {
                        boolean haveFailover = failoverBaseBranch == null
                                || failoverBaseBranch.isEmpty();
                        switch (this.onNoBranch)
                        {
                            case CREATE_BRANCH:

                        }
                        if (failoverBaseBranch == null || failoverBaseBranch.isEmpty())
                        {
                            mojo.fail("No local branch named '" + branch
                                    + "' exists, and there is no remote tracking branch "
                                    + "with the same name to branch off of.");
                        }
                    }
                }
            }
            return needingBranchCreation;
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

        boolean updateBranches(List<GitCheckout> checkouts, BuildLog buildLog,
                ProjectTree tree) throws MojoExecutionException
        {
            List<GitCheckout> toProcess = checkoutsNotAlreadyOnBranch(checkouts);
            if (toProcess.isEmpty())
            {
                buildLog.info("All matched checkouts are already on branch '" + branch + '\'');
                return false;
            }
            updateRemoteHeads(toProcess, tree, buildLog.child("updateHeads"));
            Set<GitCheckout> createLocalBranch = needingBranch(tree, toProcess, buildLog.child("branchAbsent"));
            fetchAll(toProcess);
            performCheckouts(toProcess, buildLog.child("checkout"), createLocalBranch);
            return true;
        }

        void fetchAll(Collection<? extends GitCheckout> checkouts) throws MojoExecutionException
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

        void performCheckouts(List<GitCheckout> toProcess, BuildLog log,
                Set<GitCheckout> createLocalBranch) throws MojoExecutionException
        {
            for (GitCheckout checkout : toProcess)
            {
                if (createLocalBranch.contains(checkout))
                {
                    log.info("Checkout " + checkout.name() + '[' + branch
                            + "] creating local branch for existing remote branch.");
                    checkout.createAndSwitchToBranch(branch, Optional.empty(), pretend);
                } else
                {
                    log.info("Checkout " + checkout.name() + '[' + branch + ']');
                    if (!pretend)
                    {
                        checkout.switchToBranch(branch);
                    }
                }
            }
        }
    }
}
