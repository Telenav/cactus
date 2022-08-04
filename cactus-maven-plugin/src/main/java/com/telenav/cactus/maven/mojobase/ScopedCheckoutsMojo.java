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
package com.telenav.cactus.maven.mojobase;

import com.telenav.cactus.scope.Scope;
import com.telenav.cactus.scope.ProjectFamily;
import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import static java.util.Collections.emptySet;

/**
 * Subtype of ScopeMojo for mojos that perform git operations against a set of
 * repositories, which pre-creates the project tree and collects the set of
 * repositories that match the requested scope.
 *
 * @author Tim Boudreau
 */
public abstract class ScopedCheckoutsMojo extends ScopeMojo
{
    protected ScopedCheckoutsMojo()
    {
    }

    /**
     * Create a new mojo.
     *
     * @param runFirst If true, this mojo should run on the <i>first</i>
     * invocation, in the case of an aggregator project, instead of on the last
     * - this is needed, for example, for mojos that want to change branches
     * before any code is built.
     */
    protected ScopedCheckoutsMojo(boolean runFirst)
    {
        super(runFirst);
    }

    /**
     * Create a new mojo.
     *
     * @param policy The policy for when this mojo should be run.
     */
    public ScopedCheckoutsMojo(RunPolicy policy)
    {
        super(policy);
    }

    /**
     * Run this mojo against the project tree and set of checkouts it is applied
     * to.
     *
     * @param log A log
     * @param project The project being invoked agains
     * @param myCheckout The checkout of the project invoked against
     * @param tree The project tree of all checkouts
     * @param checkouts The checkouts which matched the scope and other
     * parameters of this mojo
     * @throws Exception if something goes wrong
     */
    protected abstract void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception;

    protected final CommitMessage addCommitMessageDetail(CommitMessage msg,
            Collection<? extends GitCheckout> checkouts)
    {
        return addCommitMessageDetail(msg, checkouts, emptySet());
    }

    protected final CommitMessage addCommitMessageDetail(CommitMessage msg,
            Collection<? extends GitCheckout> checkouts, Collection<? extends Pom> projects)
    {
        if (!projects.isEmpty())
        {
            try ( CommitMessage.Section<?> sec = msg
                    .section("Affected Projects"))
            {
                projects.forEach(pom ->
                {
                    sec.bulletPoint(pom.toArtifactIdentifiers().toString());
                });
            }
        }
        if (!checkouts.isEmpty())
        {
            try ( CommitMessage.Section<?> sec = msg.section(
                    "Affected Checkouts"))
            {
                checkouts.forEach(checkout ->
                {
                    String nm = checkout.name().isEmpty()
                                ? "(root)"
                                : checkout.name();
                    sec.bulletPoint(nm);
                });
            }
        }

        return msg;
    }

    /**
     * If this mojo should fail if any checkout it operates on is locally
     * modified, return true here.
     *
     * @return false by default
     */
    protected boolean forbidsLocalModifications()
    {
        return false;
    }

    @Override
    protected final void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, Scope scope, Set<ProjectFamily> families,
            boolean includeRoot, boolean pretend) throws Exception
    {
        withProjectTree(false, tree ->
        {
            List<GitCheckout> checkouts = tree.matchCheckouts(scope,
                    myCheckout, includeRoot, families, project
                            .getGroupId());

            log.ifDebug(() ->
            {
                log.debug(
                        "Operate on the following repositories for " + scope + ":");
                checkouts.forEach(co -> log.debug("  * " + co));
            });

            if (forbidsLocalModifications())
            {
                checkLocallyModified(checkouts);
            }

            execute(log, project, myCheckout, tree, checkouts);
        });
    }

    private void checkLocallyModified(Collection<? extends GitCheckout> coll)
            throws Exception
    {
        List<GitCheckout> modified = coll
                .stream()
                .filter(GitCheckout::isDirty)
                .collect(Collectors.toCollection(() -> new ArrayList<>(coll
                .size())));
        if (!modified.isEmpty())
        {
            String message = "Some checkouts are locally modified:\n"
                    + Strings.join("\n  * ", modified);
            throw new MojoExecutionException(this, message, message);
        }
    }
}
