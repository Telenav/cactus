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
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
public class CommitMojo extends ScopedCheckoutsMojo
{
    /**
     * The commit message.
     */
    @Parameter(property = COMMIT_MESSAGE, required = true)
    private String commitMessage;

    static
    {
        System.out.println("CM: '" + COMMIT_MESSAGE + "'");
    }

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
            ProjectTree tree, List<GitCheckout> matched) throws Exception
    {
        List<GitCheckout> checkouts = matched.stream().filter(
                GitCheckout::hasUncommitedChanges)
                .collect(Collectors.toCollection(ArrayList::new));

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
        addCommitMessageDetail(msg, checkouts);

        log.info("Begin commit with message '" + commitMessage + "'");
        for (GitCheckout at : checkouts)
        {
            log.info("add/commit " + (at.name().isEmpty()
                                      ? "(root)"
                                      : at.name()));
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
            for (GitCheckout co : checkouts)
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
