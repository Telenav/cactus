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

import com.telenav.cactus.maven.scope.Scope;
import com.telenav.cactus.maven.scope.ProjectFamily;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.Optional;

/**
 * Base class for once-per-session mojos which operate within a Scope -
 * typically git operations which may be performed against a project, family of
 * projects or entire tree of projects.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
public abstract class ScopeMojo extends BaseMojo
{
    /**
     * Defines the scope this mojo operates on - used by mojos which may operate
     * on one <i>or more</i> git checkouts to determine which ones will be
     * operated on. This can be one of:
     * <ul>
     * <li><code>all</code> &mdash; Operate on every git repository below the
     * <i>submodule-root</code> of any project this mojo is run against.</li>
     * <li><code>all-java-projects</code> &mdash; Operate on every git
     * repository <b>that contains at least one <code>pom.xml</code> file</b>
     * <i>submodule-root</code> of any project this mojo is run against.</li>
     * <li><code>just_this</code> &mdash; Operate only on the git repository
     * containing the project this mojo is being run against.</li>
     * <li><code>family</code> &mdash; Operate on all git checkouts underneath
     * the submodule root owning the invoking project, where the <i>project
     * family</i>
     * is the same. The project family is the last dot-delimited substring of a
     * maven group id, omitting any suffix prefixed with a hyphen - so the
     * family of a group id <code>com.foo.bar</code> is <code>bar</code>, and so
     * is the family of a group id <code>com.foo.bar-baz</code></li>
     * <li><code>family-or-child-family</code> &mdash; The <i>project family</i>
     * either matches as described above, or is the <i>parent family</i> of a
     * project (e.g. the parent family of <code>com.foo.bar</code> is
     * <code>foo</code>).</li>
     * <li><code>same-group-id</code> &mdash; Operate on every git repository
     * containing a maven project with the same group id as the project this
     * mojo was invoked against.</li>
     * </ul>
     *
     * @see Scope#FAMILY
     */
    @Parameter(property = "telenav.scope", name = "scopeProperty",
            defaultValue = "FAMILY")
    private String scopeProperty;

    /**
     * If true, include the submodule root project even if it does not directly
     * contain a maven project matching the scope - this is important for mojos
     * which generate a new submodule commit, which in turn results in a
     * modification to the submodule parent, which now points to a different
     * commit than before, in order to ensure that a commit is generated for the
     * submodule parent updating it to point to the new commit(s).
     */
    @Parameter(property = "telenav.include-root", defaultValue = "true")
    private boolean includeRoot;

    /**
     * Override the project family, using this value instead of one derived from
     * the project's group id. Only relevant for scopes concerned with families.
     */
    @Parameter(property = "telenav.family", defaultValue = "")
    private String family;

    /**
     * If true, do not actually make changes, just print what would be done.
     */
    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    private Scope scope;

    private GitCheckout myCheckout;

    /**
     * Create a ScopeMojo that runs <i>on the last project</i> of those being
     * processed in a multi-module build.
     */
    protected ScopeMojo()
    {
        this(false);
    }

    /**
     * Create a ScopeMojo.
     *
     * @param runFirst If true, run this mojo once-per-session, on the FIRST
     * invocation; else run it once-per-session on the LAST invocation (e.g.
     * when executed against a POM project, only run after everything is built).
     */
    protected ScopeMojo(boolean runFirst)
    {
        super(runFirst
              ? RunPolicies.FIRST
              : RunPolicies.LAST); // once per session
    }

    /**
     * Do the work of this Mojo.
     *
     * @param log A log
     * @param project The project the mojo is being invoked against
     * @param myCheckout A git checkout
     * @param scope The scope
     * @param family The project family
     * @param includeRoot Whether or not the include-root property was set
     * @param pretend If true, we are in pretend-mode - log but do not do
     * @throws Exception If something goes wrong
     */
    protected abstract void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            Scope scope, ProjectFamily family, boolean includeRoot,
            boolean pretend) throws Exception;

    /**
     * Some scopes will not return a set of repositories that contain the
     * submodule root, but those that generate new commits may want to include
     * it anyway in order to update the commits it points to.
     *
     * @return true if the root is included
     */
    protected boolean isIncludeRoot()
    {
        return includeRoot;
    }

    /**
     * Generic "don't really do anything" parameter - if this returns true, the
     * subclass should not really make changes, but log what it would do as
     * accurately as possible.
     *
     * @return True if we are in pretend mode
     */
    protected boolean isPretend()
    {
        return pretend;
    }

    /**
     * Override to throw an exception if some parameters are unusable.
     *
     * @param log A log
     * @param project A project
     * @throws Exception If a parameter is invalid, preferably
     * MojoExecutionException (hint: call fail())
     */
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        // for subclasses
    }

    /**
     * Returns the project family passed explicitly, which should override that
     * of the target project when searching for git repositories to match, if
     * set.
     *
     * @return A string or null
     */
    @Override
    protected final String overrideProjectFamily()
    {
        return family == null
               ? null
               : family.trim();
    }

    /**
     * Delegates to execute().
     *
     * @param log A log
     * @param project The project
     * @throws Exception If something goes wrong
     */
    @Override
    protected final void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        execute(log, project, myCheckout, scope, projectFamily(),
                includeRoot, pretend);
    }

    /**
     * Get the scope we're using.
     *
     * @return A scope
     */
    protected Scope scope()
    {
        return scope;
    }

    /**
     * Validate the paramaters - override onValidateParameters() to perform
     * additional validation.
     *
     * @param log A log
     * @param project The project
     * @throws Exception If something is invalid
     */
    @Override
    protected final void validateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        scope = Scope.find(scopeProperty);
        Optional<GitCheckout> checkout = GitCheckout.repository(project
                .getBasedir());
        if (checkout.isEmpty())
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        myCheckout = checkout.get();
        onValidateParameters(log, project);
        if (!scope.appliesFamily() && (family != null && !"".equals(family)))
        {
            log.warn(
                    "Useless assignment of telanav.family to '" + family + "' when "
                    + "using scope " + scope + " which will not read it.  It is useful "
                    + "only with " + Scope.FAMILY + " and "
                    + Scope.FAMILY_OR_CHILD_FAMILY);
        }
    }
}
