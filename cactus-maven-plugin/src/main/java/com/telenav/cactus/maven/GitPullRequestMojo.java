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
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.System.getenv;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

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
requiresDependencyResolution = ResolutionScope.NONE,
instantiationStrategy = SINGLETON,
name = "git-pull-request", threadSafe = true)
@BaseMojoGoal("git-pull-request")
public class GitPullRequestMojo extends ScopedCheckoutsMojo
{
    
    private static final String GITHUB_CLI_TOKEN_ENV_VAR = "GH_TOKEN";
    
    /**
     * Github authentication token to use with the github cli client. If not
     * present, the GH_TOKEN environment variable must be set to a valid token.
     */
    @Parameter(property = "cactus.authentication-token", required = false)
    private String authenticationToken;
    
    /**
     * The pull request title.
     */
    @Parameter(property = "cactus.title", required = true)
    private String title;
    
    /**
     * The pull request body.
     */
    @Parameter(property = "cactus.body", required = true)
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
    
    private String searchNonce;
    
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
                                                                if (br.name().equals(targetBranch))
                                                                {
                                                                    fail("Attempting to create a pull request targeting the branch "
                                                                         + targetBranch + ", but " + coordinatesOf(project) + "'s "
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
        createPullRequest(log, project, myCheckout, tree,
                          filterToCheckoutsOnTargetBranch(log, myCheckout, tree, checkouts));
    }
    
    protected void createPullRequest(BuildLog log, MavenProject project,
                                     GitCheckout myCheckout,
                                     ProjectTree tree, Map<GitCheckout, Branch> sourceBranchForCheckout)
    throws Exception
    {
        // Filtering may have found no checkouts with changes on the target branch
        // at all, in which case, it's fine, but we're done.
        if (sourceBranchForCheckout.isEmpty())
        {
            log.error("Nothing to do.");
            return;
        }
        
        // We will build a set of tasks to perform in a specific order, so
        // we can guarantee as much is possible that we do not, say, create
        // a half-finished set of PRs because a commit or push failed AFTER
        // we have already created some of them
        List<Runnable> tasks = new ArrayList<>(sourceBranchForCheckout.size());
        
        // Collect the set of checkouts that actually need a pull request
        // performed on them into this set:
        Set<GitCheckout> createPullRequestsIn = new LinkedHashSet<>();
        
        // So we don't scare people in pretend mode
        String logPrefix = isPretend()
        ? "(pretend) "
        : "";
        
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
        tasks.add(() ->
                  {
                      log.info(
                               "Verifying existence of " + baseBranch + ", the PR destination, for "
                               + createPullRequestsIn.size() + " checkouts.");
                      // This set will be populated by the time this runs
                      createPullRequestsIn.forEach(co ->
                                                   {
                                                       // If the destination branch does not exist at all on github,
                                                       // we fail here before any destructive actions have been taken
                                                       log.info(logPrefix + "Fetch all in " + co.loggingName());
                                                       if (!isPretend())
                                                       {
                                                           co.fetchAll();
                                                       }
                                                       Branches br = tree.branches(co);
                                                       if (!br.find(baseBranch, false).isPresent())
                                                       {
                                                           fail("No branch named '" + baseBranch + "' in default remote of " + co
                                                                .loggingName());
                                                       }
                                                   });
                  });
        // Our fetches will potentially change any cached Branches instances, since
        // the remote heads may have changed, and we will test against that to
        // see if there are any commits we have that don't exist in the remote target
        // branch.
        tasks.add(tree::invalidateCache);
        
        if (commit)
        {
            CommitMessage msg = new CommitMessage(getClass(), title).paragraph(
                                                                               body);
            Set<GitCheckout> toPush = new LinkedHashSet<>();
            try ( var sect = msg.section("Creating Commits In"))
            {
                sourceBranchForCheckout.forEach((co, branch) ->
                                                {
                                                    // See if we have changes to commit
                                                    if (co.isDirty() || co.hasUntrackedFiles())
                                                    {
                                                        log.debug("Dirty or untracked files in {0}", co
                                                                  .loggingName());
                                                        sect.bulletPoint(co.loggingName());
                                                        tasks.add(() ->
                                                                  {
                                                                      log.info(logPrefix + "Add all in " + co
                                                                               .loggingName());
                                                                      if (!isPretend())
                                                                      {
                                                                          co.addAll();
                                                                      }
                                                                      log.info(logPrefix + "Commit in " + co
                                                                               .loggingName());
                                                                      if (!isPretend())
                                                                      {
                                                                          co.commit(msg.toString());
                                                                      }
                                                                  });
                                                        // Ensure we only push if *all* of our commit operations
                                                        // have succeeded, so we don't push partial patches and
                                                        // leave stuff behind
                                                        toPush.add(co);
                                                        createPullRequestsIn.add(co);
                                                    }
                                                    else
                                                        if (containsPullRequestReadyCommits(myCheckout, co,
                                                                                            branch))
                                                        {
                                                            log.debug("Have pull-request-ready commits in {0}",
                                                                      co.loggingName());
                                                            createPullRequestsIn.add(co);
                                                            // If our local branch does not exist yet, we will
                                                            // need to create it on the remote before trying to
                                                            // create a pull request involving it
                                                            if (!tree.branches(co)
                                                                .hasRemoteForLocalOrLocalForRemote(
                                                                                                   branch))
                                                            {
                                                                log.info("Will push to create branch " + branch
                                                                         + " on remote.");
                                                                sect.bulletPoint(myCheckout.loggingName()
                                                                                 + " (no commit, buut creating remote branch "
                                                                                 + branch + ")");
                                                                toPush.add(co);
                                                            }
                                                            else
                                                            {
                                                                sect.bulletPoint(myCheckout.loggingName()
                                                                                 + " (not creating commit)");
                                                            }
                                                        }
                                                        else
                                                        {
                                                            log.debug("No pull-request-ready commits in {0}", co
                                                                      .loggingName());
                                                        }
                                                });
            }
            // Now add push tasks, so that it is impossible to push anything
            // unless all of our commit tasks have already succeeded
            if (!toPush.isEmpty())
            {
                log.debug("Will push " + toPush);
                tasks.add(() ->
                          {
                              toPush.forEach(co ->
                                             {
                                                 // The remote branch for our feature branch may not exist
                                                 // yet, so figure out if we need the push to create it
                                                 // or not.
                                                 Branches theBranches = tree.branches(co);
                                                 Branch src = sourceBranchForCheckout.get(co);
                                                 assert src != null;
                                                 // If there is no remote, we need, e.g.
                                                 // `git push -u origin someLocalBranch`
                                                 boolean needCreateBranch
                                                 = !theBranches
                                                 .hasRemoteForLocalOrLocalForRemote(src);
                                                 // Log exactly what we're going to do for clarity
                                                 if (needCreateBranch)
                                                 {
                                                     log.info(logPrefix + "Push " + co.loggingName()
                                                              + " creating remote branch " + src);
                                                 }
                                                 else
                                                 {
                                                     log.info(logPrefix + "Push " + co.loggingName());
                                                 }
                                                 if (!isPretend())
                                                 {
                                                     if (needCreateBranch)
                                                     {
                                                         co.pushCreatingBranch();
                                                     }
                                                     else
                                                     {
                                                         co.push();
                                                     }
                                                 }
                                             });
                          });
                tasks.add(tree::invalidateCache);
            }
            // Provide a smidegn of searchable, unique text that can be
            // used to search on github and find the entire set of related
            // pull requests
            msg.paragraph("SearchNonce: " + searchNonce());
        }
        else
        {
            // If we are not committing anything, then just scan to find any
            // commits that exist locally but not remotely in the destination
            // branch, and if so, include it in our pull request set
            sourceBranchForCheckout.forEach((co, branch) ->
                                            {
                                                if (containsPullRequestReadyCommits(myCheckout, co, branch))
                                                {
                                                    log.debug(
                                                              "Have commits for pr in " + co.loggingName() + " on " + branch);
                                                    createPullRequestsIn.add(co);
                                                    
                                                    // Creating the pr will fail if the branch doesn't exist
                                                    // remotely, so add a push task to create it before we do
                                                    // the PR if we need to.
                                                    Branches theBranches = tree.branches(co);
                                                    boolean needCreateBranch
                                                    = !theBranches
                                                    .hasRemoteForLocalOrLocalForRemote(branch);
                                                    if (needCreateBranch)
                                                    {
                                                        tasks.add(() ->
                                                                  {
                                                                      log.info("Will push " + co.loggingName()
                                                                               + " to create remote branch for " + branch);
                                                                      if (!isPretend())
                                                                      {
                                                                          co.pushCreatingBranch();
                                                                      }
                                                                  });
                                                    }
                                                }
                                            });
        }
        // If this set is empty, then we don't actually have any changes
        // to create a pull request from
        if (createPullRequestsIn.isEmpty())
        {
            log.warn(
                     logPrefix + "No matched checkouts which are on the target branch and"
                     + "which contain commits that do not already exist remotely.");
        }
        else
        {
            String token = authenticationToken();
            // Now add a task for each checkout to the list which will actually
            // create the PR
            createPullRequestsIn.forEach(checkout ->
                                         {
                                             tasks.add(() ->
                                                       {
                                                           // We will pass the origin and destination branches to the
                                                           // github cli to ensure we create from what we think we are
                                                           Branch sourceBranch = sourceBranchForCheckout.get(checkout);
                                                           assert sourceBranch != null;
                                                           
                                                           log.info(logPrefix + "Create pull request for "
                                                                    + checkout.loggingName() + " from " + sourceBranch
                                                                    .name()
                                                                    + " to " + targetBranch);
                                                           if (!isPretend())
                                                           {
                                                               // Really create the pull request.
                                                               // We include the search nonce in the tail of the
                                                               // body text, so that there is a unique, searchable
                                                               // string to find the entire set of pull requests
                                                               // created by this run
                                                               checkout.createPullRequest(
                                                                                          token,
                                                                                          reviewers,
                                                                                          title,
                                                                                          body + "\n" + "SearchNonce: " + searchNonce() + "\n",
                                                                                          sourceBranch.name(),
                                                                                          targetBranch);
                                                           }
                                                       });
                                         });
            // Make sure branch info is cleared for any mojo that might use
            // the ProjectTree subsequently
            tasks.add(tree::invalidateCache);
            tasks.forEach(Runnable::run);
        }
        // In an IDE, this mojo can hang around, so clear state
        searchNonce = null;
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
    
    private boolean containsPullRequestReadyCommits(GitCheckout myCheckout,
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
                         + current.get().name()
                         + " but it is on the branch " + targetProjectsBranch
                         .get().name());
                return Optional.empty();
            }
            return current;
        }
    }
    
    private String searchNonce()
    {
        // Gets us a random string with a time component, which allows us
        // to include 
        return searchNonce == null
        ? searchNonce = new RandomStrings().randomChars(12) + "-"
        + Long.toString(System.currentTimeMillis() / 1000, 36)
        : searchNonce;
    }
    
    private String authenticationToken()
    {
        if (authenticationToken == null || authenticationToken.isBlank())
        {
            return getTokenFromEnvironment();
        }
        return authenticationToken;
    }
    
    private String getTokenFromEnvironment()
    {
        String result = getenv(GITHUB_CLI_TOKEN_ENV_VAR);
        if (result == null)
        {
            fail("-Dcactus.authentication-token not passed, and GH_TOKEN "
                 + "environment variable is unset");
        }
        return result;
    }
    
    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
    throws Exception
    {
        if (authenticationToken == null || authenticationToken.isBlank())
        {
            // Will fail if not present
            getTokenFromEnvironment();
        }
        if (title.isBlank())
        {
            fail("Must supply title");
        }
        if (body.isBlank())
        {
            fail("Must supply body");
        }
    }
}
