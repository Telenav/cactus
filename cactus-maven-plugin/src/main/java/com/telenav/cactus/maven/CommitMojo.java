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

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.LinkedHashSet;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Set;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PUSH;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.COMMIT_MESSAGE;

/**
 * Performs a git commit, with the passed <code>commit-message</code> which
 * <b>must</b> be supplied (try enclosing the -D argument entirely on the
 * command-line, e.g. <code>'-Dcommit-message=Some commit message'</code>).
 * <p>
 * The scope for which commits are generated is FAMILY by default, generating
 * commits for all git sub-repositories of the subrepo parent which share a
 * project family (derived from the project's groupId). Passing ALL will change
 * it to any repos containing modified sources). JUST_THIS will commit only the
 * repository that owns the current project.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "commit", threadSafe = true)
@BaseMojoGoal("commit")
public class CommitMojo extends ScopedCheckoutsMojo
{
    /**
     * The commit message.
     */
    @Parameter(property = COMMIT_MESSAGE, required = true)
    private String commitMessage;

    /**
     * If true, do not call <code>git add -A</code> before committing - only
     * commit that which has been manually staged.
     */
    @Parameter(property = "cactus.commit.skip.add", defaultValue = "false")
    private boolean skipAdd;

    /**
     * If true, push after committing. If no remote branch of the same name as
     * the local branch exists, one will be created.
     */
    @Parameter(property = PUSH, defaultValue = "false")
    private boolean push;

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.warn("No matched checkouts contain local modifications.");
            return;
        }

        GitCheckout root = tree.root();
        if (isIncludeRoot() && !checkouts.contains(root))
        {
            checkouts.add(root);
        }

        // Make sure nobody is in detached-head state, or we'll create a commit
        // that is not on any branch, and the changes will be un-findable if we
        // switch to a branch post-commit.
        for (GitCheckout checkout : checkouts)
        {
            if (checkout.isDetachedHead())
            {
                fail("Checkout is in detached-head state but has local changes. "
                        + "Switch to a branch before committing or you risk losing "
                        + "track of them.");
            }
        }

        CommitMessage msg = new CommitMessage(CommitMojo.class, commitMessage);
        Set<GitCheckout> toCommit = new LinkedHashSet<>();
        StringBuilder nameList = new StringBuilder();
        for (GitCheckout co : checkouts)
        {
            if (co.hasUncommitedChanges())
            {
                toCommit.add(co);
            }
            if (nameList.length() > 0)
            {
                nameList.append(", ");
            }
            nameList.append(co.loggingName());
        }
        if (toCommit.isEmpty())
        {
            log.warn("Nothing to commit among " + nameList);
            return;
        }
        addCommitMessageDetail(msg, toCommit);

        if (isVerbose())
        {
            log.info("Begin commit with message '" + commitMessage + "'");
        }
        for (GitCheckout at : toCommit)
        {
            log.info("add/commit " + at.loggingName());
            if (!isPretend())
            {
                if (!skipAdd)
                {
                    at.addAll();
                }
                at.commit(msg.toString());
            }
        }
        if (push)
        {
            for (GitCheckout co : toCommit)
            {
                switch (co.needsPush())
                {
                    case YES:
                        co.push();
                        break;
                    case REMOTE_BRANCH_DOES_NOT_EXIST:
                        co.pushCreatingBranch();
                        break;
                }
            }
        }
    }
}
