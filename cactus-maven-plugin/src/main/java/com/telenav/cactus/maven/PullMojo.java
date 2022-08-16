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
import com.telenav.cactus.git.Conflicts.Conflict;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.SKIP_CONFLICTS;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Performs a (careful) git pull on any checkouts in the tree that need it,
 * scoped to project family, all, just this checkout or all checkouts with a
 * project of the same group id, using the <code>scope</code> property.
 * <p>
 * If <code>telenav.permit.local.modifications</code> is set to true, pulls will
 * be attempted even with modified sources.
 * </p>
 * <p>
 * Checkouts which are in detached-head state (no branch to pull from) are
 * skipped.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "pull", threadSafe = true)
@BaseMojoGoal("pull")
public class PullMojo extends ScopedCheckoutsMojo
{
    /**
     * If true, allow for local modifications to be present.
     */
    @Parameter(property = "cactus.permit-local-modifications",
            defaultValue = "true")
    private boolean permitLocalModifications;

    /**
     * If true, skip (and log) pulling any checkouts which will fail with
     * conflicts, rather than aborting early.
     */
    @Parameter(property = SKIP_CONFLICTS,
            defaultValue = "false")
    private boolean skipConflicts;

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts)
            throws Exception
    {
        List<GitCheckout> needingPull = needingPull(checkouts);
        if (needingPull.isEmpty())
        {
            log.info("Nothing to pull. All projects are up to "
                    + "date with remote.");
        }
        else
        {
            String pfx = isPretend()
                         ? "(pretend) "
                         : "";

            GitCheckout root = tree.root();
            if (!root.isSubmoduleRoot())
            {
                root = null;
            }
            Map<GitCheckout, Conflicts> allConflicts = new TreeMap<>();
            for (GitCheckout checkout : needingPull)
            {
                log.info("Fetch to determine if merge can succeed.");
                ifNotPretending(checkout::fetch);
                Conflicts conflicts = checkout.checkForConflicts();

                if (!conflicts.isEmpty() && conflicts.hasHardConflicts())
                {
                    if (!checkout.equals(root) 
//                            && conflicts.isGitmodulesOnlyConflict()
                            )
                    {
                        allConflicts.put(checkout, conflicts);
                    }
                    else
                    {
                        log.info("Ignoring gitmodules conflict " + conflicts
                                + " can be solved with a rebase.");
                    }
                }
            }
            if (!allConflicts.isEmpty())
            {
                StringBuilder sb = new StringBuilder(
                        "Pull will cause conflicts in " + allConflicts.size() + " git checkouts:");
                allConflicts.forEach((checkout, conflicts) ->
                {
                    sb.append("\n  * ").append(checkout.loggingName());
                    for (Conflict cflict : conflicts)
                    {
                        sb.append("\n    * ").append(cflict);
                    }
                });
                if (skipConflicts)
                {
                    PrintMessageMojo.publishMessage(sb, session(), false);
                    needingPull.removeAll(allConflicts.keySet());
                    if (needingPull.isEmpty())
                    {
                        log.warn(
                                "All repos to pull have conflicts.  Nothing to do.");
                        return;
                    }
                }
                else
                {
                    fail(sb);
                }
            }

            for (GitCheckout checkout : needingPull)
            {
                log.info(pfx + "Pull " + checkout.loggingName());
                System.out.println("Pull " + pfx + checkout.loggingName());
                if (!isPretend())
                {
                    checkout.pull();
                }
            }
        }
    }

    @Override
    protected boolean forbidsLocalModifications()
    {
        return !permitLocalModifications;
    }

    private List<GitCheckout> needingPull(Collection<? extends GitCheckout> cos)
    {
        return cos.stream()
                .filter(co -> isPretend()
                              ? co.needsPull()
                              : co.updateRemoteHeads().needsPull()
                || co.remoteHead().map(h -> !h.equals(co.head())).orElse(
                        false))
                .collect(Collectors.toCollection(() -> new ArrayList<>(cos
                .size())));
    }
}
