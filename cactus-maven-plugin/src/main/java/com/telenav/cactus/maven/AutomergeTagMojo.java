package com.telenav.cactus.maven;

import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.common.CactusCommonPropertyNames;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.AutomergeTag;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.DEFAULT_STABLE_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PUSH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.STABLE_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.TARGET_BRANCH;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "automerge-tag", threadSafe = true)
@BaseMojoGoal("automerge-tag")
public class AutomergeTagMojo extends ScopedCheckoutsMojo
{

    @Parameter(property = STABLE_BRANCH, defaultValue = DEFAULT_STABLE_BRANCH)
    private String stableBranch;

    @Parameter(property = TARGET_BRANCH, required = false)
    private String targetBranch;

    @Parameter(property = PUSH, defaultValue = "true", required = false)
    private boolean push;

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        StringBuilder notOnBranchCheckouts = new StringBuilder();
        checkouts.forEach(checkout ->
        {
            Branches branches = tree.branches(checkout);
            if (!branches.currentBranch().isPresent())
            {
                if (notOnBranchCheckouts.length() > 0)
                {
                    notOnBranchCheckouts.append(", ");
                }
                notOnBranchCheckouts.append(checkout.loggingName());
            }
        });
        if (notOnBranchCheckouts.length() > 0)
        {
            notOnBranchCheckouts.insert(0,
                    "Some targeted checkouts are not on a branch: ");
            fail(notOnBranchCheckouts);
        }
        String targetBranch = targetBranch(myCheckout, tree);

        Set<GitCheckout> needingTag = filterCheckoutsWithCommitNotOnBranch(
                targetBranch, stableBranch, tree, log, isPretend(), checkouts);
        if (needingTag.isEmpty())
        {
            log.warn(
                    "No checkouts on the branch " + targetBranch + " have commits "
                    + "not already in the remote of branch " + stableBranch);
            return;
        }
        applyAutomergeTag(automergeTag(), isPretend(), log, needingTag, push);
    }

    String targetBranch(GitCheckout myCheckout, ProjectTree tree)
    {
        if (targetBranch != null)
        {
            return targetBranch;
        }
        String result = tree.branches(myCheckout).currentBranch().get().name();
        if (CactusCommonPropertyNames.DEFAULT_DEVELOPMENT_BRANCH.equals(result)
                || stableBranch.equals(result))
        {
            fail("The target branch may not be the stable or develop branches");
        }
        return result;
    }

    static void applyAutomergeTag(AutomergeTag tag, boolean pretend,
            BuildLog log, Collection<? extends GitCheckout> checkouts,
            boolean push)
    {
        for (GitCheckout checkout : checkouts)
        {
            log.info("Tag " + checkout.loggingName() + " with " + tag);
            if (!pretend)
            {
                checkout.tag(tag.toString(), false);
            }
        }
        if (push)
        {
            for (GitCheckout checkout : checkouts)
            {
                log.info("Push tag " + tag + " to " + checkout.loggingName());
                if (!pretend) {
                    boolean result = checkout.pushTag(tag.toString());
                    if (!result) {
                        log.warn("Could not push tag " + tag + " to " 
                                + checkout.loggingName() + ".  Does it have a remote?");
                    }
                }
            }
        }
    }

    static Set<GitCheckout> automergeTag(String branchNameOrNull,
            String stableBranch, ProjectTree tree, BuildLog log,
            boolean pretend,
            Collection<? extends GitCheckout> potentialCheckouts,
            boolean push,
            Supplier<AutomergeTag> tagSupplier)
    {
        Set<GitCheckout> result = filterCheckoutsWithCommitNotOnBranch(
                branchNameOrNull,
                stableBranch, tree, log, pretend, potentialCheckouts);
        if (!result.isEmpty())
        {
            AutomergeTag tag = tagSupplier.get();
            applyAutomergeTag(tag, pretend, log, result, push);
        }
        return result;
    }

    static Set<GitCheckout> filterCheckoutsWithCommitNotOnBranch(
            String branchName,
            String stableBranch,
            ProjectTree tree,
            BuildLog log,
            boolean pretend,
            Collection<? extends GitCheckout> checkouts)
    {
        Set<GitCheckout> result = new LinkedHashSet<>();
        checkouts.forEach(checkout ->
        {
            log.info("Fetch all for " + checkout.loggingName());
            if (!pretend)
            {
                checkout.updateRemoteHeads();
                checkout.fetchAll();
                tree.invalidateBranches(checkout);
            }
        });

        checkouts.forEach(checkout ->
        {
            Branches branches = tree.branches(checkout);
            Optional<Branch> remoteBranch = branches
                    .find(stableBranch, false);
            if (!remoteBranch.isPresent())
            {
                log.info(
                        "Checkout " + checkout.loggingName() + " does not have "
                        + "a branch named " + stableBranch + ". Skipping.");
                return;
            }
            Branch curr = branches.currentBranch().get();
            String currentBranchName = curr.name();
            if (stableBranch.equals(currentBranchName))
            {
                log.info(checkout.loggingName() + " is on the " + stableBranch
                        + " branch - will not tag commits on the "
                        + "stable destination branch.");
                return;
            }
            String targetBranchName = branchName;
            if (targetBranchName == null)
            {
                targetBranchName = currentBranchName;
            }
            // Only take checkouts on a same named branch
            if (currentBranchName.equals(targetBranchName))
            {
                // If we have a remote of the stable branch, check to make
                // sure we aren't tagging commits that are already on it
                String head = checkout.head();
                if (!pretend)
                {
                    checkout.updateRemoteHeads();
                    checkout.fetchAll();
                }
                String stableHead = checkout.headOf(stableBranch);
                // The remote stable branch's head is the same as the local
                // head, or the local head is an ancestor of the remote stable
                // branch head, then there are no new commits to be built - everything
                // we have locally (and perhaps more) was previously built and
                // merged, so there is no point in creating a tag and doing it again.
                if (remoteBranch.isPresent()
                        && (head.equals(stableHead)
                        || checkout.isAncestor(head, stableHead)))
                {
                    log.info("Skip automerge tag for branch "
                            + targetBranchName + " of "
                            + checkout.loggingName()
                            + " - the remote of "
                            + stableBranch
                            + " already contains all of its commits.");
                    return;
                }
                // Include the checkout
                result.add(checkout);
            }
        });
        return result;
    }

}
