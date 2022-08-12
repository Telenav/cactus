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

import com.mastfrog.util.strings.RandomStrings;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.NeedPushResult;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.commit.CommitMessage.Section;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.task.TaskSet;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

/**
 * Starts and finishes branches according to git flow branching conventions.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresOnline = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        name = "git-pull-request", threadSafe = true)
@BaseMojoGoal("git-pull-request")
public class GitPullRequestMojo extends AbstractGithubMojo
{

    /**
     * The pull request title.
     */
    @Parameter(property = "cactus.title", required = false)
    private String title;

    /**
     * The pull request body.
     */
    @Parameter(property = "cactus.body", required = false)
    private String body;

    /**
     * The reviewers to request.
     */
    @Parameter(property = "cactus.reviewers", defaultValue = "")
    private String reviewers;

    /**
     * If true (the default), generate commits in any repositories that are
     * matched and contain modifications or untracked, unignored files.
     */
    @Parameter(property = "cactus.commit", defaultValue = "true")
    private boolean commit;

    /**
     * The base branch which new feature-branches should be created from, and
     * which, if createBranchesIfNeeded is false, should be used as the fallback
     * branch to put checkouts on if the target branch does not exist.
     */
    @Parameter(property = "cactus.base-branch", defaultValue = "develop")
    String baseBranch = "develop";

    /**
     * The branch from which the pull request should be created; if unset, the
     * current branch in the checkout is used.
     */
    @Parameter(property = "cactus.target-branch")
    String targetBranch;

    /**
     * If true, open a browser tab with each new pull request.
     */
    @Parameter(property = "cactus.open", defaultValue = "true")
    boolean open;

    private String searchNonce;

    private final Set<GitCheckout> fetched = ConcurrentHashMap.newKeySet();

    @Override
    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        ClassloaderLog._log(project, this);
        if (Objects.equals(baseBranch, targetBranch))
        {
            fail("Base branch and target branch are the same: " + targetBranch);
        }
        if (title != null && title.isBlank())
        {
            fail("title / cactus.title is empty");
        }
        if (body != null && body.isBlank())
        {
            fail("body / cactus.body is empty");
        }
        if ((title == null) != (body == null))
        {
            fail("Either title and body must both be set, or both unset.");
        }
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.info("No checkouts matched.");
            return;
        }

        tree.branches(myCheckout).currentBranch().ifPresent(br ->
        {
            if (br.name().equals(baseBranch))
            {
                fail("Attempting to create a pull request targeting the branch "
                        + baseBranch + ", but " + coordinatesOf(project) + "'s "
                        + "checkout is already on that branch.  Either switch to "
                        + "the branch you want to create a pull request from, or "
                        + "run this mojo against a project in the right project family "
                        + "which is in a checkout that is on the branch you want to "
                        + "create the pull request from.");
            }
        });
        // Run the logic with a winnowed-down set of git checkouts which are on a
        // branch with the same name as the branch the target project is on, or if
        // targetBranch was set, on a branch with that name
        Map<GitCheckout, Branch> sourceBranchForCheckout
                = filterToCheckoutsOnTargetBranch(log, myCheckout, tree,
                        checkouts);
        // Filtering may have found no checkouts with changes on the target branch
        // at all, in which case, it's fine, but we're done.
        if (sourceBranchForCheckout.isEmpty())
        {
            log.error("Nothing to do.");
            return;
        }
        try
        {
            // So we don't scare people in pretend mode
            BuildLog plog = isPretend()
                            ? log.child("(pretend)")
                            : log;
            createPullRequests(plog, project, myCheckout, tree,
                    sourceBranchForCheckout);
        }
        finally
        {
            tree.invalidateCache();
            clearPRCache();
            fetched.clear();
            synchronized (this)
            {
                searchNonce = null;
            }
        }
    }

    protected void createPullRequests(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, Map<GitCheckout, Branch> sourceBranchForCheckout)
            throws Exception
    {
        TaskSet tasks = TaskSet.newTaskSet(log);

        Set<GitCheckout> alreadyHavePRs
                = checkoutsWithExistingPrs(sourceBranchForCheckout, log);

        if (alreadyHavePRs.equals(sourceBranchForCheckout.keySet()))
        {
            log.warn("Every checkout matched already has an open, mergeable "
                    + "PR.  Nothing to do");
            return;
        }
        else
        {
            alreadyHavePRs.forEach(sourceBranchForCheckout::remove);
            log.info("Pruned " + alreadyHavePRs);
        }

        // First add a task that will verify that a remote branch we want
        // to make the target of the pull request actually exists for all of
        // the repositories we're trying to create a PR in - this should always
        // be the case, but since it can be explicitly set, it is possible
        // for it to be garbage.
        //
        // We make a point of doing a git fetch --all ahead of making any
        // determination, to ensure if the destination branch was created since
        // the local checkout was cloned, we don't fail erronously because the
        // local checkout doesn't know about it.
        //
        // The point of adding this first is to fail early, before we have
        // committed or pushed anything if the actual PR creation cannot possibly
        // succeed
        tasks.group("Verify base branch '" + baseBranch + "'", grp ->
        {
            sourceBranchForCheckout.forEach((co, branch)
                    -> grp.add(
                            "Check '" + baseBranch + "' exists in "
                            + co.loggingName(), () ->
                    {
                        log.info("Fetch all in " + co.loggingName());
                        ensureUpToDateRemoteHeads(co, tree);
                        Branches br = tree.branches(co);
                        if (!br.find(baseBranch, false).isPresent())
                        {
                            fail("No branch named '" + baseBranch + "' in default remote of "
                                    + co.loggingName());
                        }
                    }));
        });

        Set<GitCheckout> toPush = new LinkedHashSet<>();
        Set<GitCheckout> dirtyCheckouts = new HashSet<>();

        collectDirtyCheckoutsAndCreateCommitTasks(sourceBranchForCheckout,
                toPush, dirtyCheckouts, tasks);

        if (!commit && dirtyCheckouts.isEmpty())
        {
            fail("Commit is false and " + dirtyCheckouts.size() + " repositories have "
                    + " local modifications.  Running `gh pr create` in such a "
                    + "situation will trigger interactive questions and "
                    + "cannot be automated: " + dirtyCheckouts);
        }

        tasks.group("Push Changes", pushTasks ->
        {
            Set<GitCheckout> needingBranchCreation = new HashSet<>();
            sourceBranchForCheckout.forEach((checkout, branch) ->
            {
                if (!toPush.contains(checkout)
                        && containsPullRequestReadyCommitsPendingPush(myCheckout,
                                checkout, branch))
                {
                    toPush.add(checkout);
                }
                else
                {
                    ensureUpToDateRemoteHeads(checkout, tree);
                    Branches branches = tree.branches(checkout);
                    if (!branches.find(branch.name(), false).isPresent())
                    {
                        toPush.add(checkout);
                        needingBranchCreation.add(checkout);
                    }
                    else
                    {
                        NeedPushResult np = checkout.needsPush();
                        if (np.canBePushed())
                        {
                            toPush.add(checkout);
                            if (np.needCreateBranch())
                            {
                                needingBranchCreation.add(checkout);
                            }
                        }
                    }
                }
            });
            for (GitCheckout checkout : toPush)
            {
                if (needingBranchCreation.contains(checkout))
                {
                    pushTasks.add(
                            "Push " + checkout.loggingName() + " creating remote branch "
                            + sourceBranchForCheckout.get(checkout),
                            () -> ifNotPretending(checkout::pushCreatingBranch));
                }
                else
                {
                    pushTasks.add("Push branch " + sourceBranchForCheckout.get(
                            checkout) + " of "
                            + checkout.loggingName(),
                            () -> ifNotPretending(checkout::push));
                }
            }
        });

        sourceBranchForCheckout.forEach((checkout, sourceBranch) ->
        {
            System.out.println(
                    "CHERK1 " + checkout.loggingName() + " " + sourceBranch);
        });

        tasks.group("Really create pull requests", prTasks ->
        {
            sourceBranchForCheckout.forEach((checkout, sourceBranch) ->
            {

                System.out.println(
                        "CHERK " + checkout.loggingName() + " " + sourceBranch);

                String ttl = "Create pull request for "
                        + checkout.loggingName() + " from "
                        + sourceBranch.name() + " to " + baseBranch;

                prTasks.add(ttl, () ->
                {
                    if (!isPretend())
                    {
                        // Really create the pull request.
                        // We include the search nonce in the tail of the
                        // body text, so that there is a unique, searchable
                        // string to find the entire set of pull requests
                        // created by this run
                        URI uri = checkout.createPullRequest(
                                this,
                                reviewers,
                                titleOrSyntheticTitle(sourceBranchForCheckout),
                                bodyOrSyntheticBody(sourceBranchForCheckout),
                                sourceBranch.name(),
                                baseBranch);
                        log.info("Created " + uri);
                        // Ensure we print the output in quiet mode:
                        System.out.println(uri);
                        if (open)
                        {
                            open(uri, log);
                        }
                    }
                });
            });
        });

        System.out.println("Plan:\n" + tasks);
        tasks.execute();
    }

    private void collectDirtyCheckoutsAndCreateCommitTasks(
            Map<GitCheckout, Branch> sourceBranchForCheckout,
            Set<GitCheckout> toPush, Set<GitCheckout> dirtyCheckouts,
            TaskSet tasks)
    {
        CommitMessage msg = new CommitMessage(getClass(),
                titleOrSyntheticTitle(sourceBranchForCheckout))
                .paragraph(bodyOrSyntheticBody(sourceBranchForCheckout));

        try ( Section<?> sect = msg.section("Committing In"))
        {
            for (Map.Entry<GitCheckout, Branch> e : sourceBranchForCheckout
                    .entrySet())
            {
                if (e.getKey().isDirty() || e.getKey().hasUntrackedFiles())
                {
                    toPush.add(e.getKey());
                    dirtyCheckouts.add(e.getKey());
                    sect.bulletPoint(e.getKey().loggingName() + " - " + e
                            .getValue().name());
                }
            }
        }
        if (commit)
        {
            tasks.group("Commit any pending changes", grp ->
            {
                dirtyCheckouts.forEach(checkout
                        -> grp.add(
                                "Commit changes in " + checkout.loggingName(),
                                () -> ifNotPretending(() ->
                                {
                                    checkout.addAll();
                                    checkout.commit(msg.toString());
                                })));
            });
        }
    }

    private void open(URI uri, BuildLog log)
    {
        if (Desktop.isDesktopSupported())
        {
            log.info("Opening browser for " + uri);
            try
            {
                Desktop.getDesktop().browse(uri);
            }
            catch (IOException ex)
            {
                log.error("Exception thrown opening " + uri, ex);
            }
        }
        else
        {
            log.error("Desktop not supported in this JVM; cannot open " + uri);
        }
    }

    private Map<GitCheckout, Branch> filterToCheckoutsOnTargetBranch(
            BuildLog log, GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts)
    {
        // Prune out any git checkouts that are not on a branch with the
        // right name, from the set that were matched against the family
        // or whatever scope we're running under
        Map<GitCheckout, Branch> result = new LinkedHashMap<>(checkouts.size());
        checkouts.forEach(co
                -> prSourceBranchFor(log, myCheckout, co, tree).ifPresent(
                        sourceBranch -> result.put(co, sourceBranch)));
        return result;
    }

    private boolean containsPullRequestReadyCommitsPendingPush(
            GitCheckout myCheckout,
            GitCheckout co, Branch sourceBranchForThisCheckout)
    {
        // This will tell us if there are any differences at all, but not
        // whether we are ahead or behind the remote head
        if (co.isNotAtSameHeadAsBranch(baseBranch))
        {
            // Get the set of branches that contain the head
            Branches containingCommit = co.branchesContainingCommit(co.head());
            // If find() returns a Branch object, then the remote destination branch
            // already contains the head commit we would be using for our pull request - so
            // there are no changes to create a pull request for this checkout from
            return !containingCommit.find(sourceBranchForThisCheckout.name(),
                    false).isPresent();
        }
        return false;
    }

    private Optional<Branch> prSourceBranchFor(BuildLog log,
            GitCheckout myCheckout,
            GitCheckout checkout, ProjectTree tree)
    {
        Branches branches = tree.branches(checkout);
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
                    return Optional.of(br);
                }
                return Optional.empty();
            });
        }
        else
        {
            // Find out what branch the project we're RUNNING AGAINST is on,
            // and create a PR only for other matched checkouts which are on
            // a branch with the same name, so we create PRs from all branches
            // in the matched checkouts which are on a branch named feature/foo,
            // but do NOT create PRs for other checkouts which might contain
            // unpushed commits, but are not on the branch we are using
            Branches targetProjectBranches = tree.branches(myCheckout);
            Optional<Branch> targetProjectsBranch
                    = targetProjectBranches.currentBranch();

            // The project we were run against is in detached-head state - we
            // have to fail here, as there is no way to track down a feature-branch
            // name to look for in other checkouts
            if (!targetProjectsBranch.isPresent())
            {
                // This will throw and get us out of here
                fail("Target project " + coordinatesOf(project())
                        + " in " + project().getBasedir()
                        + " is not on a branch.  It needs to be to match "
                        + "same-named branches in other checkouts to "
                        + "decide what to create the pull request from.");
            }

            Optional<Branch> current = branches.currentBranch();
            if (!current.isPresent())
            {
                // If the checkout we are queried about is in detached head state, don't
                // use it, but log a warning.
                log.warn("Ignoring " + checkout.loggingName() + " for pull "
                        + "request - it is not on any branch.");
                return current;
            }
            if (!current.get().name().equals(targetProjectsBranch.get().name()))
            {
                // If the checkout we are queried about *is* on some branch, but
                // not the right one, also ignore it and log that at level info:
                log.info(
                        "Ignoring matched checkout " + checkout.loggingName() + " for pull "
                        + "request - because we are matching the branch "
                        + targetProjectsBranch.get().name()
                        + " but it is on the branch " + current.get().name());
                return Optional.empty();
            }
            else
            {
                log.info("Will include " + checkout.loggingName()
                        + " in the pull request set, on branch "
                        + targetProjectsBranch.get().name());
            }
            return current;
        }
    }

    private synchronized String searchNonce()
    {
        // Gets us a random string with a time component, which allows us
        // to include 
        return searchNonce == null
               ? searchNonce = new RandomStrings().randomChars(12) + "-"
                + Long.toString(System.currentTimeMillis() / 1000, 36)
               : searchNonce;
    }

    void ensureUpToDateRemoteHeads(GitCheckout co, ProjectTree tree)
    {
        if (fetched.add(co))
        {
            ifNotPretending(() ->
            {
                co.fetchAll();
                tree.invalidateBranches(co);
            });
        }
    }

    private Set<GitCheckout> checkoutsWithExistingPrs(
            Map<GitCheckout, Branch> in, BuildLog log)
    {
        // Find all the checkouts that already have an open PR 
        Set<GitCheckout> result = new HashSet<>(in.size());
        in.forEach((checkout, branch) ->
        {
            List<MinimalPRItem> existingPrs
                    = openPullRequestsForBranch(
                            baseBranch, branch.name(), checkout);
            if (!existingPrs.isEmpty())
            {
                result.add(checkout);
            }
        });
        return result;
    }

    private String titleOrSyntheticTitle(
            Map<GitCheckout, Branch> sourceBranchForCheckout)
    {
        return title == null
               ? "New pull request in ~" + sourceBranchForCheckout.size() + " projects: " + searchNonce()
               : title + " " + searchNonce();
    }

    private String bodyOrSyntheticBody(
            Map<GitCheckout, Branch> sourceBranchForCheckout)
    {
        StringBuilder result = new StringBuilder();
        if (body != null)
        {
            result.append(body);
        }
        result.append("\n\n").append("SearchNonce: ").append(searchNonce())
                .append('\n');
        appendProjectInfo(sourceBranchForCheckout, result);
        return result.toString();
    }

    private void appendProjectInfo(
            Map<GitCheckout, Branch> sourceBranchForCheckout, StringBuilder into)
    {
        into.append("In any or all of");
        sourceBranchForCheckout.forEach((co, br) ->
        {
            into.append(' ').append(co.loggingName()).append(':').append(br
                    .name());
        });
        into.append('.');
    }

}
