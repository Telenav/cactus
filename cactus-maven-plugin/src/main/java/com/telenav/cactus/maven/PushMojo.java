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

import com.telenav.cactus.git.Conflicts;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.NeedPushResult;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.AutomergeTag;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.git.GitCheckout.depthFirstSort;
import static com.telenav.cactus.maven.PrintMessageMojo.publishMessage;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.*;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Perform a git push in any projects that need it, scoped by the
 * <code>scope</code> property to family, all, just-this, etc.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "push", threadSafe = true)
@BaseMojoGoal("push")
public class PushMojo extends ScopedCheckoutsMojo
{
    static Map<GitCheckout, NeedPushResult> collectPushKinds(
            Collection<? extends GitCheckout> checkouts)
    {
        Map<GitCheckout, NeedPushResult> result = new HashMap<>();
        for (GitCheckout co : checkouts)
        {
            NeedPushResult res = co.needsPush();
            if (res.canBePushed())
            {
                result.put(co, res);
            }
        }
        return result;
    }

    static boolean needPull(GitCheckout checkout)
    {
        return checkout.mergeBase().map((String mergeBase)
                -> checkout.remoteHead().map((String remoteHead)
                        -> checkout.head().equals(mergeBase)).orElse(false))
                .orElse(false);
    }

    /**
     * If true, do not abort if the repository to be pushed contains local
     * modifications - this is usually an indication that committing was
     * neglected, but there are occasions when it is desirable.
     */
    @Parameter(property = PERMIT_LOCAL_CHANGES,
            defaultValue = "true")
    private boolean permitLocalModifications;

    /**
     * If true, use <code>git push --all</code> to push all local branches, not
     * just the current one checked out.
     */
    @Parameter(property = "cactus.push.all", defaultValue = "false")
    private boolean pushAll;

    /**
     * If true, skip pushing repositories where the push would fail with a
     * conflict and alert the operator instead of aborting before pushing
     * anything.
     */
    @Parameter(property = SKIP_CONFLICTS, defaultValue = "false")
    private boolean skipConflicts;

    @Parameter(property = STABLE_BRANCH, defaultValue = DEFAULT_STABLE_BRANCH)
    private String stableBranch;

    @Parameter(property = CREATE_AUTOMERGE_TAG, defaultValue = "false")
    private boolean createAutomergeTag;

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        GitCheckout root = tree.root();
        if (isIncludeRoot() && !checkouts.contains(tree.root()))
        {
            checkouts.add(tree.root());
        }
        // Depth first sort, so we process the submodule root last, in
        // case commits to child modules put it into the dirty state.
        List<Map.Entry<GitCheckout, NeedPushResult>> needingPush
                = depthFirstSort(collectPushKinds(checkouts));
        if (needingPush.isEmpty())
        {
            log.info("No projects needing push in " + needingPush);
        }
        else
        {
            pullIfNeededAndPush(log, project, needingPush, tree, root);
        }
        if (isIncludeRoot() && (root.hasUncommitedChanges() || root
                .hasUntrackedFiles() || root.needsPush().canBePushed()))
        {
            CommitMessage msg = new CommitMessage(PushMojo.class,
                    "Updating heads for push of " + (checkouts.size() - 1)
                    + " checkouts");
            msg.section("Submodules", section ->
            {
                needingPush.forEach(entry ->
                {
                    section.bulletPoint(entry.getKey().loggingName()
                            + " " + entry.getValue() + " -> " + entry.getKey()
                            .head().substring(0, 7));
                });
            });

            log.info("Create commit and push in root " + root.checkoutRoot()
                    .getFileName());
            ifNotPretending(() ->
            {
                root.addAll();
                root.commit(msg.toString());
            });
            ifNotPretending(root::push);
            if (createAutomergeTag)
            {
                AutomergeTagMojo.automergeTag(null, stableBranch, tree, log,
                        isPretend(), singleton(root), true, this::automergeTag);
            }
        }
    }

    @Override
    protected boolean forbidsLocalModifications()
    {
        return !permitLocalModifications;
    }

    private Set<GitCheckout> checkNeedPull(
            List<Map.Entry<GitCheckout, NeedPushResult>> needingPush,
            BuildLog log)
    {
        Set<GitCheckout> needingPull = new LinkedHashSet<>();
        for (Map.Entry<GitCheckout, NeedPushResult> co : needingPush)
        {
            GitCheckout checkout = co.getKey();
            log.debug("Update remote heads: " + checkout.name());
            if (!isPretend())
            {
                checkout.updateRemoteHeads();
            }
            if (!co.getValue().needCreateBranch() && checkout.needsPull())
            {
                log.debug("Needs pull: " + checkout.name());
                needingPull.add(checkout);
            }
        }
        return needingPull;
    }

    private Set<GitCheckout> pull(Set<GitCheckout> needingPull, BuildLog log,
            GitCheckout submoduleRoot)
    {
        // We can be in a single git checkout with no submodules:
        boolean rootIsRoot = submoduleRoot.isSubmoduleRoot();
        Map<GitCheckout, Conflicts> allConflicts = new TreeMap<>();
        if (!needingPull.isEmpty())
        {
            log.warn("Needing pull:");

            for (GitCheckout checkout : needingPull)
            {
                if (rootIsRoot && submoduleRoot.equals(checkout))
                {
                    // Submodule root is special, if it is only conflicts
                    // in submodule checkouts
                    continue;
                }
                Conflicts cf = checkout.checkForConflicts();
                if (!cf.isEmpty() && cf.hasHardConflicts())
                {
                    allConflicts.put(checkout, cf.filterHard());
                }
            }
            if (!allConflicts.isEmpty())
            {
                StringBuilder sb = new StringBuilder(
                        "Conflicts - " + allConflicts.size()
                        + " checkouts cannot be pushed");
                allConflicts.forEach((repo, cf) ->
                {
                    sb.append("\n  * ").append(repo.loggingName());
                    cf.forEach(c ->
                    {
                        sb.append("\n    * ").append(c);
                    });
                });
                if (skipConflicts)
                {
                    needingPull.removeAll(allConflicts.keySet());
                    publishMessage(sb, session(), false);
                }
                else
                {
                    fail(sb);
                }
            }

            for (GitCheckout checkout : needingPull)
            {
                log.info("Pull " + checkout);
                if (!isPretend())
                {
                    if (checkout.equals(submoduleRoot) && rootIsRoot)
                    {
                        checkout.pullWithRebase();
                    }
                    else
                    {
                        checkout.pull();
                    }
                }
            }
        }
        return new HashSet<>(allConflicts.keySet());
    }

    private void pullIfNeededAndPush(BuildLog log, MavenProject project,
            List<Map.Entry<GitCheckout, NeedPushResult>> needingPush,
            ProjectTree tree,
            GitCheckout submoduleRoot)
    {
        Set<GitCheckout> needingPull = checkNeedPull(needingPush, log.child(
                "checkNeedPull"));
        Set<GitCheckout> skipped = pull(needingPull, log.child("pull"),
                submoduleRoot);
        Set<GitCheckout> tagged = emptySet();
        if (createAutomergeTag)
        {
            Set<GitCheckout> toTag = new LinkedHashSet<>();
            needingPush.forEach(e ->
            {
                // Skip the submodule root - we don't want to tag it now
                if (!submoduleRoot.equals(e.getKey()) && !skipped.contains(e
                        .getKey()))
                {
                    toTag.add(e.getKey());
                }
            });
            tagged = AutomergeTagMojo.automergeTag(null,
                    stableBranch,
                    tree,
                    log.child("automerge-tag"),
                    isPretend(),
                    toTag,
                    false,
                    this::automergeTag);
        }
        push(needingPush, log.child("push"));
        if (!pushAll && !tagged.isEmpty())
        {
            AutomergeTag tag = automergeTag();
            for (GitCheckout taggedRepo : tagged)
            {
                log.info("Push tag " + tag + " to " + taggedRepo.loggingName());
                ifNotPretending(() -> taggedRepo.pushTag(tag.toString()));
            }
        }
    }

    private void push(List<Map.Entry<GitCheckout, NeedPushResult>> needingPush,
            BuildLog log)
    {
        log.warn("Begin push.");
        for (Map.Entry<GitCheckout, NeedPushResult> co : needingPush)
        {
            GitCheckout checkout = co.getKey();
            if (co.getValue().needCreateBranch())
            {
                log.info("Push creating branch: " + checkout);
                if (!isPretend())
                {
                    if (pushAll)
                    {
                        checkout.pushAll();
                    }
                    else
                    {
                        checkout.pushCreatingBranch();
                    }
                }
            }
            else
            {
                log.info("Push: " + checkout);
                emitMessage(checkout);
                if (!isPretend())
                {
                    if (pushAll)
                    {
                        checkout.pushAll();
                    }
                    else
                    {
                        checkout.push();
                    }
                }
            }
        }
    }
}
