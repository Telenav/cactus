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

import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.DEFAULT_DEVELOPMENT_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PUSH;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "merge", threadSafe = true)
@BaseMojoGoal("merge")
public class MergeBranchMojo extends ScopedCheckoutsMojo
{
    /**
     * Name of the branch to merge <b>from</b> - if unset, uses the current
     * branch.
     */
    @Parameter(property = "cactus.merge.from")
    String mergeFrom;

    /**
     * The branch to merge into - the default is "develop".
     */
    @Parameter(property = "cactus.merge.into",
            defaultValue = DEFAULT_DEVELOPMENT_BRANCH)
    String mergeInto;

    /**
     * If true (the default), use <code>-X ours</code> to automatically prefer
     * changes from the branch being merged in case of a conflict.
     */
    @Parameter(property = "cactus.merge.clobber", defaultValue = "true")
    boolean clobber;

    /**
     * A second branch to merge into - e.g. merge a release branch back to
     * "develop" but first also merge it into release/current.
     */
    @Parameter(property = "cactus.also.merge.into")
    String alsoMergeInto;

    /**
     * Delete the merged branch after a successful merge.
     */
    @Parameter(property = "cactus.delete.merged.branch", defaultValue = "false")
    boolean deleteMergedBranch;

    /**
     * If true, create a git tag named from the latter portion of the target
     * branch name following any / character, if the merge succeeds.
     */
    @Parameter(property = "cactus.tag", defaultValue = "true")
    boolean tag;

    /**
     * If true, push on success.
     */
    @Parameter(property = PUSH, defaultValue = "false")
    boolean push;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        validateBranchName(mergeFrom, true);
        validateBranchName(mergeInto, false);
        validateBranchName(alsoMergeInto, true);
        switch (scope())
        {
            case FAMILY:
                break;
            default:
                if (families().isEmpty())
                {
                    fail("Cannot use cactus.families exception cactus.scope=family");
                }
        }
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        // Pending: We could use git merge-tree, scanning for conflict
        // markers in the diff it emits, to detect if a merge will fail.
        // For what this mojo will be used for, that is likely to be rare.
        //
        // Get maps of GitCheckout:Branch for from, to and also
        withBranches(tree, checkouts, (toMergeFrom, toMergeTo, alsoMergeTo) ->
        {
            if (toMergeFrom.isEmpty() || toMergeTo.isEmpty())
            {
                log.warn("No checkouts found to merge");
                return;
            }
            log.info("Have " + toMergeFrom.size() + " checkouts to merge.");
            List<GitCheckout> checkoutsToMerge = GitCheckout.depthFirstSort(
                    new HashSet<>(toMergeTo.keySet()));

            checkoutsToMerge.retainAll(toMergeFrom.keySet());
            // Do the thing, one repo at a time:
            for (GitCheckout checkout : checkoutsToMerge)
            {
                Branch from = toMergeFrom.get(checkout);
                Branch to = toMergeTo.get(checkout);
                Branch also = alsoMergeTo.get(checkout);
                if (checkout.equals(tree.root()))
                {
                    // The checkout root needs a different strategy - it is handled
                    // last, and will always already have the right new heads since
                    // we already merged each submodule.  So we just need to generate
                    // a commit of the heads, then a merge commit with any file changes,
                    // using -X theirs to prefer remote (read: committed since the last merge)
                    // changes.
                    mergeDownSubmoduleRoot(also, log, checkout, from, to);
                    mergeDownSubmoduleRoot(to, log, checkout, from, to);
                    continue;
                }

                // If we have secondary branch to merge to, do that first,
                // so we leave the repo on the final destination branch
                if (also != null)
                {
                    log.info(
                            "First merge " + from + " into " + also + " in " + checkout
                                    .loggingName());
                    ifNotPretending(() ->
                    {
                        if (also.isRemote())
                        {
                            log.info(
                                    "Branch " + also.trackingName() + " does not exist "
                                    + "locally.  Creating it.");
                            checkout.createAndSwitchToBranch(also.name(),
                                    Optional.of(also.trackingName()));
                        }
                        else
                        {
                            checkout.switchToBranch(also.name());
                        }
                        if (clobber)
                        {
                            checkout.mergeWithClobber(from.name());
                        }
                        else
                        {
                            checkout.merge(from.name());
                        }
                    });
                    if (push)
                    {
                        log.info("Push " + checkout.loggingName());
                        ifNotPretending(checkout::push);
                    }
                }
                log.info("Merge " + from + " into " + to + " in " + checkout
                        .loggingName());
                ifNotPretending(() ->
                {
                    // Get on the target branch
                    checkout.switchToBranch(to.name());
                    // Do the merge
                    checkout.merge(from.name());
                });
                if (tag)
                {
                    // Strip any leading feature/ or whatever from the branch name
                    String newTag = tagName(from);
                    log.info(
                            "Tag " + checkout.loggingName() + " with " + newTag);
                    ifNotPretending(() ->
                    {
                        // Use tag -f to force - this would be a silly thing to
                        // fail on.
                        checkout.tag(newTag, true);
                    });
                }
                if (deleteMergedBranch)
                {
                    // Nuke it.
                    log.info("Delete branch " + from + " in " + checkout
                            .loggingName());
                    ifNotPretending(() ->
                    {
                        checkout.deleteBranch(from.name(), to.name(), false);
                    });
                }
                if (push)
                {
                    // The target branch may not exists, which requires a
                    // different push call
                    boolean remoteBranchExists = tree.branches(checkout).find(to
                            .name(), false).isPresent();
                    if (remoteBranchExists)
                    {
                        checkout.push();
                    }
                    else
                    {
                        checkout.pushCreatingBranch();
                    }
                }
            }
        });
    }

    public void mergeDownSubmoduleRoot(Branch targetBranchToMergeInto,
            BuildLog log,
            GitCheckout checkout, Branch from, Branch to)
    {
        // For the root checkout, we just need to create a commit, because the
        // submodules are already there
        if (targetBranchToMergeInto != null)
        {
            if (targetBranchToMergeInto.isRemote())
            {
                log.info("Branch " + targetBranchToMergeInto.trackingName()
                        + " does not exist locally.  Creating it.");
                ifNotPretending(() ->
                {
                    checkout.createAndSwitchToBranch(targetBranchToMergeInto
                            .name(),
                            Optional.of(targetBranchToMergeInto.trackingName()));
                });
            }
            else
            {
                log.info("Switch " + checkout.loggingName() + " to branch "
                        + targetBranchToMergeInto.name());
                checkout.switchToBranch(targetBranchToMergeInto.name());
            }
            if (checkout.isDirty())
            {
                log.info(
                        "Generate a commit with submodule changes in " + checkout
                                .loggingName() + " on " + targetBranchToMergeInto
                                .name());
                ifNotPretending(() ->
                {
                    checkout.addAll();
                    CommitMessage msg = new CommitMessage(getClass(),
                            "Apply submodule updates");
                    msg.append("Apply submodule updates from " + from + " to "
                            + to + " and also " + targetBranchToMergeInto
                            + " in " + checkout.loggingName());
                    checkout.commit(msg.toString());
                });
            }

            log.info(
                    "Merge " + from + " into " + targetBranchToMergeInto + " for "
                    + checkout.loggingName() + " with -X theirs");
            ifNotPretending(() ->
            {
                checkout.mergeWithClobber(from.name());
            });
            if (checkout.isDirty())
            {
                ifNotPretending(() ->
                {
                    log.info(
                            "Generate a merge commit for fiile changes in " + checkout
                                    .loggingName() + " on " + targetBranchToMergeInto
                                    .name());
                    checkout.addAll();
                    CommitMessage msg = new CommitMessage(
                            getClass(),
                            "Merge submodule updates from " + from);
                    msg.append(
                            "Merge submodule updates from " + from + " to " + to
                            + " and also " + targetBranchToMergeInto
                            + " in " + checkout.loggingName());
                    checkout.commit(msg.toString());
                });
            }
        }
    }

    private void withBranches(ProjectTree tree, List<GitCheckout> checkouts,
            ThrowingTriConsumer<Map<GitCheckout, Branch>, Map<GitCheckout, Branch>, Map<GitCheckout, Branch>> c)
            throws Exception
    {
        if (isIncludeRoot() && !checkouts.contains(tree.root()))
        {
            checkouts = new ArrayList<>(checkouts);
            checkouts.add(tree.root());
        }
        try
        {
            // Collect all our branch info
            Map<GitCheckout, Branch> toMergeFrom = new LinkedHashMap<>();
            Map<GitCheckout, Branch> toMergeTo = new LinkedHashMap<>();
            Map<GitCheckout, Branch> additionalDestinations = new LinkedHashMap<>();
            for (GitCheckout checkout : checkouts)
            {
                Branches branches = tree.branches(checkout);

                Optional<Branch> branchToMergeFrom;
                if (mergeFrom != null)
                {
                    // We have an explicit branch
                    branchToMergeFrom = branches.find(mergeFrom, true);
                }
                else
                {
                    // Just use whatever the local branch is
                    branchToMergeFrom = branches.currentBranch();
                }
                // If something is missing, that just means it was not touched
                // in whatever performed the changes
                branchToMergeFrom.ifPresent(branch -> toMergeFrom.put(checkout,
                        branch));

                branches.find(mergeInto, true).ifPresent(
                        branchToMergeTo
                        -> toMergeTo.put(checkout,
                                branchToMergeTo));
                if (alsoMergeInto != null)
                {
                    branches.find(alsoMergeInto, true)
                            .or(() -> branches.find(alsoMergeInto, false))
                            .ifPresent(also ->
                            {
                                additionalDestinations.put(checkout, also);
                            });
                }
            }

            // Winnow out those we don't have a branch for on both sides:
            Set<GitCheckout> common = new HashSet<>(toMergeTo.keySet());
            common.retainAll(toMergeFrom.keySet());
            Set<GitCheckout> toRemove = new HashSet<>(toMergeTo.keySet());
            toRemove.addAll(toMergeFrom.keySet());
            toRemove.removeAll(common);
            for (GitCheckout rem : toRemove)
            {
                toMergeFrom.remove(rem);
                toMergeTo.remove(rem);
            }
            toRemove.clear();
            // We also may have picked up repos where we would merge a branch
            // into itself, if the target branch name was unspecified.  Prune
            // them too.
            toMergeFrom.forEach((repo, branch) ->
            {
                if (branch.equals(toMergeTo.get(repo)))
                {
                    toRemove.add(repo);
                }
            });
            for (GitCheckout rem : toRemove)
            {
                toMergeFrom.remove(rem);
                toMergeTo.remove(rem);
            }

            c.accept(toMergeFrom, toMergeTo, additionalDestinations);
        }
        finally
        {
            tree.invalidateCache();
        }
    }

    private String tagName(Branch fromBranch)
    {
        String nm = fromBranch.name();
        int ix = nm.lastIndexOf('/');
        if (ix > 0 && ix < nm.length() - 1)
        {
            return nm.substring(ix + 1);
        }
        return nm;
    }
}
