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

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Gets the current checkout, or all checkouts containing projects with the current project's groupId, or all checkouts
 * in the entire tree - filtering them to only checkouts that contain a pom.xml in the root, and does one of:
 * <ul>
 * <li>If a branch is specified, ensures the checkout is on the head of that
 * branch, creating it if needed (and createBranchesIfNeeded is set)</li>
 * <li>If no branch is specified, attempts to find the branch the majority of
 * checkouts containing the same group id are on, and sets it to that, creating
 * it if needed</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "dev-prep", threadSafe = true)
public class DevelopmentPrepMojo extends ScopedCheckoutsMojo
{

    @Parameter(property = "telenav.auto-fix-branches", defaultValue = "false")
    boolean autoFixBranches = false;

    @Parameter(property = "telenav.create-branches-if-needed",
               defaultValue = "true")
    boolean createBranchesIfNeeded = true;

    @Parameter(property = "telenav.branch", name = "branch")
    String branchName;

    @Parameter(property = "telenav.base-branch", defaultValue = "develop")
    String baseBranch = "develop";

    @Override
    protected void execute(BuildLog log, MavenProject project,
                           GitCheckout myCheckout,
                           ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {

        //        if (branchName != null)
        //        {
        //            ensureOnBranch(tree, log.child("branch-to:" + branchName), project, checkouts);
        //        } else
        //        {
        //            ensureOnSomeConsistentBranch(tree, log.child("ensure-some-branch"), project);
        //        }
    }

    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        validateBranchName(branchName, true);
        validateBranchName(baseBranch, false);
    }
    /*
    private void ensureOnBranch(ProjectTree tree, BuildLog log, MavenProject project, List<GitCheckout> checkouts)
            throws Exception
    {
        Map<GitCheckout, String> toMove;
        if (thisCheckoutOnly)
        {
            toMove = Collections.singletonMap(myCheckout, baseBranch);
        } else
        {
            toMove = new TreeMap<>();
            tree.allCheckouts().forEach(checkout ->
            {
                if (checkout.hasPomInRoot())
                {
                    Optional<String> currentBranch = tree.branchFor(checkout);
                    if (thisGroupIdOnly)
                    {
                        if (!tree.groupIdsIn(checkout).contains(project.getGroupId()))
                        {
                            return;
                        }
                    }
                    if (!currentBranch.isPresent() || !branchName.equals(currentBranch.get()))
                    {
                        System.out.println("ADD " + checkout.checkoutRoot().getFileName());
                        toMove.put(checkout, branchName);
                    }
                }
            });
        }
        if (!toMove.isEmpty())
        {
            // Once without doing anything, to fail-fast without changing any branches
            moveCheckoutsToBranches(toMove, log, tree, true);
            // And again to really do it, now that we know it can work
            moveCheckoutsToBranches(toMove, log, tree, false);
        } else
        {
            log.info("All checkouts already on the branch '" + branchName + "'");
        }
    }

    private void ensureOnSomeConsistentBranch(ProjectTree tree, BuildLog log, MavenProject project)
            throws Exception
    {
        Map<GitCheckout, String> changes = new HashMap<>();
        if (thisCheckoutOnly)
        {
            if (!myCheckout.isDetachedHead())
            {
                log.info("Single repo, already on a branch, nothing to do.");
                return;
            }
            log.info("Single repo, not on a branch - will move to '"
                    + baseBranch + "'.");
            changes.put(myCheckout, baseBranch);
        } else
        {
            Predicate<GitCheckout> filter;
            if (thisGroupIdOnly)
            {
                filter = checkout ->
                {
                    return tree.groupIdsIn(checkout).contains(project.getGroupId());
                };
            } else
            {
                filter = ignored -> true;
            }

            tree.allCheckouts().stream().filter(filter).filter(GitCheckout::hasPomInRoot).forEach(checkout ->
            {
                if (checkout.isSubmoduleRoot())
                {
                    return;
                }
                Set<String> gids = tree.groupIdsIn(checkout);
                System.out.println("   gits " + gids + " in " + checkout);
                if (gids.size() > 1)
                {
                    String msg = "More than one group id in non-root checkout " + checkout
                            + ".  Will not guess what branch to move this to.  "
                            + "Explicitly specify a branch instead with "
                            + "-Dtelenav.branch=someBranch";
                    // Quietly throw:
                    Exceptions.chuck(new MojoExecutionException(this, msg, msg));
                } else
                {
                    if (gids.isEmpty())
                    {
                        // This should have already been filtered out, but throwing a
                        // NoSuchElementException would be non-intuitive
                        log.warn("No group ids at all for checkout " + checkout);
                        return;
                    }
                }
                Optional<String> targetBranch = tree.mostCommonBranchForGroupId(gids.iterator().next());
                String target = targetBranch.orElse(baseBranch);
                boolean needAdd = tree.isDetachedHead(checkout);
                System.out.println("  detached head " + needAdd + " for " + checkout);
                if (!needAdd)
                {
                    Optional<String> currentBranch = tree.branchFor(checkout);
                    needAdd = !currentBranch.isPresent() || !target.equals(currentBranch.get());
                }
                if (needAdd)
                {
                    changes.put(checkout, target);
                }
            });
        }
        if (!changes.isEmpty())
        {
            // Once to fail fast without making changes
            moveCheckoutsToBranches(changes, log, tree, true);
            // And once to make changes
            moveCheckoutsToBranches(changes, log, tree, false);
        } else
        {
            log.info("Nothing to do.");
        }
    }

    private void moveCheckoutsToBranches(Map<GitCheckout, String> targetBranchToMoveToForCheckout,
            BuildLog childLog, ProjectTree tree, boolean pretend) throws MojoExecutionException
    {
        if (!targetBranchToMoveToForCheckout.isEmpty())
        {
            childLog.info("Have " + targetBranchToMoveToForCheckout.size()
                    + " checkouts to change branches for");

            Map<Path, String> submoduleBranchesToUpdate = new HashMap<>();
            for (Map.Entry<GitCheckout, String> e : targetBranchToMoveToForCheckout.entrySet())
            {
                GitCheckout checkout = e.getKey();
                String branchToChangeTo = e.getValue();
                if (autoFixBranches)
                {
                    childLog.info("Move to branch '" + branchToChangeTo + "' for " + checkout);
                    Branches branches = e.getKey().branches();
                    Optional<Branch> branch = branches.find(branchToChangeTo, true);
                    if (!branch.isPresent())
                    {
                        if (createBranchesIfNeeded)
                        {
                            if (checkout.hasUncommitedChanges() && !checkout.isSubmoduleRoot())
                            {
                                if (tree.branchFor(checkout).isPresent() && !tree.branchFor(checkout).get().equals(baseBranch))
                                {
                                    throw new MojoExecutionException("Cannot create a new branch named '"
                                            + branchToChangeTo + " in " + checkout
                                            + " because it contain uncommited changes.");
                                }
                            }

                            boolean success = checkout.createAndSwitchToBranch(branchToChangeTo,
                                    Optional.ofNullable(baseBranch), pretend);
                            checkout.submoduleRelativePath().ifPresent(relativePath ->
                            {
                                submoduleBranchesToUpdate.put(relativePath, branchToChangeTo);
                            });
                            if (!success)
                            {
                                throw new MojoExecutionException("Create and switch to branch "
                                        + branchToChangeTo + " failed for " + checkout);
                            }
                        } else
                        {
                            throw new MojoExecutionException("Local branch '"
                                    + e.getValue() + "' does not exist for "
                                    + e.getKey() + " and createBranchesIfNeeded is not set to true."
                                    + " Cannot switch to that branch.");
                        }
                    } else
                    {
                        boolean switched = pretend ? true : checkout.switchToBranch(branchToChangeTo);
                        if (!switched)
                        {
                            throw new MojoExecutionException("Failed to switch to branch "
                                    + branchToChangeTo + " in " + checkout);
                        }
                        checkout.submoduleRelativePath().ifPresent(relativePath ->
                        {
                            submoduleBranchesToUpdate.put(relativePath, branchToChangeTo);
                        });
                    }
                } else
                {
                    childLog.info("autoFixBranches is false - branch should be "
                            + branchToChangeTo + " for " + checkout);
                }
            }
            if (!autoFixBranches && !pretend)
            {
                throw new MojoExecutionException(this, "Some checkouts need their branches changed, "
                        + "but telenav.autoFixBranches is false",
                        "Some checkouts need their branches changed: " + targetBranchToMoveToForCheckout);
            }
            if (!pretend && targetBranchToMoveToForCheckout.containsKey(tree.root()))
            {
                log.info("Updating .gitmodules.");
                StringBuilder msg = new StringBuilder("Automated .gitmodules branch update moving");
                for (Map.Entry<Path, String> e : submoduleBranchesToUpdate.entrySet())
                {
                    tree.root().setSubmoduleBranch(e.getKey().toString(), e.getValue());
                    msg.append("\n * ").append(e.getKey()).append(" to ").append(e.getValue());
                }
                if (tree.root().hasUncommitedChanges())
                {
                    log.info("Committing updated .gitmodules");
                    tree.root().addAll();
                    tree.root().commit(msg.toString());
                }
                tree.invalidateCache();
            }
        }
    }
     */
}
