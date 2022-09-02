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
import com.telenav.cactus.tasks.TaskSet;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.mastfrog.util.strings.Strings.join;
import static com.telenav.cactus.maven.ClassloaderLog._log;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.BASE_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.COMMIT_CHANGES;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.DEFAULT_DEVELOPMENT_BRANCH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.TARGET_BRANCH;
import static com.telenav.cactus.tasks.TaskSet.newTaskSet;
import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;
import static java.lang.System.currentTimeMillis;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

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
        defaultPhase = VALIDATE,
        requiresOnline = true,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "git-pull-request", threadSafe = true)
@BaseMojoGoal("git-pull-request")
public class GitHubPullRequestMojo extends AbstractGithubMojo
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
    @Parameter(property = COMMIT_CHANGES, defaultValue = "true")
    private boolean commit;

    /**
     * The base branch which new feature-branches should be created from, and
     * which, if createBranchesIfNeeded is false, should be used as the fallback
     * branch to put checkouts on if the target branch does not exist.
     */
    @Parameter(property = BASE_BRANCH, defaultValue = DEFAULT_DEVELOPMENT_BRANCH)
    String baseBranch;

    /**
     * The branch from which the pull request should be created; if unset, the
     * current branch in the checkout is used.
     */
    @Parameter(property = TARGET_BRANCH)
    String targetBranch;

    /**
     * If true, open a browser tab with each new pull request.
     */
    @Parameter(property = "cactus.open", defaultValue = "true")
    boolean open;

    private String searchNonce;

    // Ensures we don't run git fetch --all more than once per repo
    private final Set<GitCheckout> fetched = newKeySet();
    // Retain URLs of generated PRs so a "related PRs" section in
    // subsequent ones' body can reference them
    private final Set<String> uris = newKeySet();
    // In pretend-mode, we want to log the body and title we would use,
    // but not be irritating about it
    private volatile boolean titleLogged;
    private volatile boolean bodyLogged;

    @Override
    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        _log(project, this);
        if (Objects.equals(baseBranch, targetBranch))
        {
            fail("Base branch and target branch are the same: " + targetBranch);
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

        if (targetBranch == null)
        {
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
        }
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
            uris.clear();
        }
    }

    protected void createPullRequests(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, Map<GitCheckout, Branch> sourceBranchForCheckout)
            throws Exception
    {
        TaskSet tasks = newTaskSet(log);

        Set<GitCheckout> alreadyHavePRs
                = checkoutsWithExistingPrs(sourceBranchForCheckout, log);

        // Every repository we matched already has a pull request using the
        // target branch - we're done
        if (alreadyHavePRs.equals(sourceBranchForCheckout.keySet()))
        {
            log.warn("Every checkout matched already has an open, mergeable "
                    + "PR.  Nothing to do.");
            return;
        }
        else
        {
            // Remove an repos where a PR already exists
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
        tasks.group(
                "Ensure base branch '" + baseBranch + "' exists in all targets",
                grp ->
        {
            // This is a corner case, but handle it, as it would be
            // bad to create some pull requests and then kaboom.
            sourceBranchForCheckout.forEach((co, branch)
                    -> grp.add(
                            "Ensure '" + baseBranch + "' exists in "
                            + co.loggingName(), () ->
                    {
                        // Make sure we really know what the remote head
                        // is, and if we didn't know locally about the
                        // destination branch, that we refresh our metadata
                        // so there's a record of the destination branch when
                        // we look for it
                        log.info("Fetch all in " + co.loggingName());
                        // This will also discard the Branches instance cached
                        // by ProjectTree, so we don't keep any stale lists of branches
                        ensureUpToDateRemoteHeads(co, tree);
                        // Get the set of all branches our git checkout knows about,
                        // local or remote
                        Branches br = tree.branches(co);
                        if (!br.find(baseBranch, false).isPresent())
                        {
                            // And bang, we'red one - we're trying to create a PR
                            // that wants to be merged to something that doesn't exist
                            fail("No branch named '" + baseBranch + "' in default remote of "
                                    + co.loggingName());
                        }
                    }));
        });

        // Collect repos that need pushes here
        Set<GitCheckout> toPush = new LinkedHashSet<>();
        // And ones which are dirty here, so we can fail if there
        // are any and commit=false
        Set<GitCheckout> dirtyCheckouts = new HashSet<>();

        // Do the dirty check and add tasks to our task set for
        // committing if needed and allowed
        collectDirtyCheckoutsAndCreateCommitTasks(sourceBranchForCheckout,
                toPush, dirtyCheckouts, tasks);

        if (!commit && dirtyCheckouts.isEmpty())
        {
            // Uh oh - this would toss `gh` into "What should I do?"
            // interactive mode and Maven would appear to hang forever.
            fail("Commit is false and " + dirtyCheckouts.size() + " repositories have "
                    + " local modifications.  Running `gh pr create` in such a "
                    + "situation will trigger interactive questions and "
                    + "cannot be automated: " + dirtyCheckouts);
        }

        // Create a group for our pushes (if we don't add any child tasks
        // to it, it won't appear in the plan)
        tasks.group("Push Changes", pushTasks ->
        {
            Set<GitCheckout> needingBranchCreation = new HashSet<>();
            sourceBranchForCheckout.forEach((checkout, branch) ->
            {
                if (!toPush.contains(checkout)
                        && containsPullRequestReadyCommitsPendingPush(myCheckout,
                                checkout, branch))
                {
                    // Definitely needs a push - the remote has a branch
                    // for our PR branch, but it does not have all the commits
                    // we have locally
                    toPush.add(checkout);
                }
                else
                {
                    // Once again, make sure we have up to date fetch heads (if
                    // we did this above, it won't be repeated)
                    ensureUpToDateRemoteHeads(checkout, tree);
                    Branches branches = tree.branches(checkout);
                    // Figure out if the branch doesn't exist remotely,
                    // and flag it so we can `git push -u origin theBranch`
                    // instead of just `git push`
                    if (!branches.find(branch.name(), false).isPresent())
                    {
                        toPush.add(checkout);
                        needingBranchCreation.add(checkout);
                    }
                    else
                    {
                        NeedPushResult np = checkout.needsPush();
                        // If it needs pushing for any other reason, deal with
                        // that now.  We MUST not call `gh` with unpushed commits
                        // on the PR branch or we're dead.
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
            // Now actually add the push tasks
            for (GitCheckout checkout : toPush)
            {
                // Create a push or push that creates a remote branch,
                // as needed
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
                            () -> ifNotPretending(() ->
                            {
                                checkout.push();
                                tree.invalidateBranches(checkout);
                            }));
                }
            }
        });

        Set<GitCheckout> pruned = new HashSet<>();
        tasks.group("Prune checkouts with no head difference", grp ->
        {
            sourceBranchForCheckout.forEach((checkout, sourceBranch) ->
            {
                grp.add("Check branch difference in " + checkout.loggingName(),
                        () ->
                {
                    String head = checkout.headOf(sourceBranch.name());
                    Branches branches = tree.branches(checkout);
                    branches.find(baseBranch, false).ifPresent(branch ->
                    {
                        String remoteHead = checkout.headOf(branch
                                .trackingName());
                        if (remoteHead != null)
                        {
                            if (remoteHead.equals(head) || checkout.isAncestor(
                                    head, remoteHead))
                            {
                                log.info(
                                        "Will skip " + checkout.loggingName() + " - "
                                        + " the local head " + head + " is or is an ancestor of "
                                        + " the remote head " + remoteHead);
                                pruned.add(checkout);
                            }
                        }
                    });
                });
            });
        });

        // Now add the tasks for really creating the PR
        tasks.group("Create pull requests", prTasks ->
        {
            prTasks.add("Check all pruned", () ->
            {
                if (pruned.equals(sourceBranchForCheckout.keySet()))
                {
                    String msg = "All checkouts were pruned because their heads match or "
                            + " are ancestors of the remote head: "
                            + join(',', pruned, GitCheckout::loggingName);
                    System.out.println(
                            "All checkouts were pruned because their heads");
                    log.warn(msg);
                }
            });
            sourceBranchForCheckout.forEach((checkout, sourceBranch) ->
            {
                if (pruned.contains(checkout))
                {
                    log.info("Not generating PR for " + checkout.loggingName()
                            + " because it has no new commits.");
                    return;
                }

                String logMsg = "Create pull request for "
                        + checkout.loggingName() + " from "
                        + sourceBranch.name() + " to " + baseBranch;

                prTasks.add(logMsg, () ->
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
                        if (uri != null)
                        {
                            // Collect the URI, so subsequent PRs can have
                            // a list of related PRs
                            uris.add(uri.toURL().toString());
                            log.info("Created " + uri);
                            // Ensure we print the output in quiet mode:
                            emitMessage(uri);
                            if (open)
                            {
                                open(uri, log);
                            }
                        }
                    }
                });
            });
        });

        log.warn("Execution Plan:\n" + tasks);
        // And run our pile of work
        tasks.execute();
    }

    private void collectDirtyCheckoutsAndCreateCommitTasks(
            Map<GitCheckout, Branch> sourceBranchForCheckout,
            Set<GitCheckout> toPush, Set<GitCheckout> dirtyCheckouts,
            TaskSet tasks)
    {
        // Create a commit message we can use across all commits,
        // if any
        CommitMessage msg = new CommitMessage(getClass(),
                titleOrSyntheticTitle(sourceBranchForCheckout))
                .paragraph(bodyOrSyntheticBody(sourceBranchForCheckout));

        // Decorate the commit message with information about what
        // we're doing, and populate dirtyCheckouts and toPush
        try ( Section<?> sect = msg.section("Committing In"))
        {
            for (Map.Entry<GitCheckout, Branch> e : sourceBranchForCheckout
                    .entrySet())
            {
                if (e.getKey().isDirty() || e.getKey().hasUntrackedFiles())
                {
                    // If we're committing, we definitely need to push - mark
                    // it as such, as we will test for unpushed commits before
                    // the commit has run
                    toPush.add(e.getKey());
                    // Collect this into dirty checkouts, which was passed in
                    // to us - it is also used to fail in the case we would 
                    // dump gh into interactive mode because commit=false
                    dirtyCheckouts.add(e.getKey());
                    // And note what it is we're doing in the commit message
                    sect.bulletPoint(e.getKey().loggingName()
                            + " - " + e.getValue().name());
                }
            }
        }

        // If we need to commit anything, add tasks for that
        if (commit)
        {
            tasks.group("Commit any pending changes", grp ->
            {
                dirtyCheckouts.forEach(checkout
                        -> grp.add(
                                "Commit changes in " + checkout.loggingName(),
                                () -> ifNotPretending(() ->
                                {
                                    // git add -A
                                    checkout.addAll();
                                    // git ci -m msg
                                    checkout.commit(msg.toString());
                                })));
            });
        }
    }

    private void open(URI uri, BuildLog log)
    {
        // Get out of the way of the rest of maven
        // execution - initializing hunks of AWT is not free.
        if (isDesktopSupported())
        {
            log.info("Opening browser for " + uri);
            try
            {
                getDesktop().browse(uri);
            }
            catch (IOException ex)
            {
                log.error("Exception thrown opening " + uri, ex);
            }
        }
        else
        {
            log.error(
                    "Desktop not supported in this JVM; cannot open " + uri);
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
                return empty();
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
               ? searchNonce = new RandomStrings().randomChars(10) + "-"
                + Long.toString(currentTimeMillis() / 1_000, 36)
               : searchNonce;
    }

    void ensureUpToDateRemoteHeads(GitCheckout co, ProjectTree tree)
    {
        // Avoid doing this repeatedly
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
        String result;
        if (title == null)
        {
            // Get the head commit message from the first matched
            // repository
            assert !sourceBranchForCheckout.isEmpty();
            GitCheckout co = sourceBranchForCheckout.entrySet().iterator()
                    .next().getKey();

            // Assign it to title, for consistency and to avoid
            // excessive external process runs
            result = title = co.commitMessage(sourceBranchForCheckout.get(
                    co).trackingName());
        }
        else
        {
            result = title;
        }
        // In pretend mode, print out what we would put in the PR
        if (isPretend() && !titleLogged)
        {
            titleLogged = true;
            emitMessage("TITLE: " + title);
        }
        return result;
    }

    private String bodyOrSyntheticBody(
            Map<GitCheckout, Branch> sourceBranchForCheckout)
    {
        // It's not a commit message, but the metadata and
        // structure are what we want:
        CommitMessage msg = new CommitMessage(getClass(),
                titleOrSyntheticTitle(
                        sourceBranchForCheckout));
        if (body != null)
        {
            msg.paragraph(body);
        }
        if (!uris.isEmpty())
        {
            // Include the URLs of any pull requests we've already
            // created
            try ( Section<CommitMessage> sect = msg.section(
                    "Related Pull Requests"))
            {
                for (String u : uris)
                {
                    sect.bulletPoint(u);
                }
            }
        }

        // Ensure we describe all of the places one should look
        // for related pull requests - the first PR created will
        // not be able to list the related ones
        try ( Section<CommitMessage> sect = msg.section(
                "Creating Pull Requests In"))
        {
            sourceBranchForCheckout.forEach((checkout, branch) ->
            {
                sect.bulletPoint(
                        checkout.loggingName() + " " + branch + " -> " + baseBranch);
            });
        }
        // We include a "search nonce" for ease of searching; the
        // invoking project is worth knowing if something goes wrong.
        try ( Section<CommitMessage> sect = msg.section("Metadata"))
        {
            sect.bulletPoint(
                    "Invoked on " + project().getGroupId() + ":" + project()
                    .getArtifactId() + ":" + project().getVersion());
            sect.bulletPoint("SearchNonce: " + searchNonce());
        }
        // In pretend mode, print out what we would put in the PR
        if (isPretend() && !bodyLogged)
        {
            bodyLogged = true;
            emitMessage("BODY:\n" + msg);
        }
        return msg.toString();
    }

}
