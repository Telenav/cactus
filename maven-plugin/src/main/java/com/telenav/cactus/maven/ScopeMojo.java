package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Base class for once-per-session mojos which operate within a Scope -
 * typically git operations which may be performed against a project, family of
 * projects or entire tree of projects.
 *
 * @author Tim Boudreau
 */
public abstract class ScopeMojo extends BaseMojo
{
    // Common properties used by most or all
    /**
     * Defines the scope this mojo operates on - used by mojos which may operate
     * on one <i>or more</i> git checkouts to determine which ones will be
     * operated on. This can be one of:
     * <ul>
     * <li><code>all</code> &mdash; Operate on every git repository below the
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
     * <li><code>family_or_child_family</code> &mdash; The <i>project family</i>
     * either matches as described above, or is the <i>parent family</i> of a
     * project (e.g. the parent family of <code>com.foo.bar</code> is
     * <code>foo</code>).</li>
     * <li><code>same_group_id</code> &mdash; Operate on every git repository
     * containing a maven project with the same group id as the project this
     * mojo was invoked against.</li>
     * </ul>
     *
     * @see Scope#FAMILY
     */
    @Parameter(property = "telenav.scope", defaultValue = "FAMILY", name = "scope")
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

    protected ScopeMojo()
    {
        super(true); // once per session
    }

    @Override
    protected final void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        scope = Scope.find(scopeProperty);
        Optional<GitCheckout> checkout = GitCheckout.repository(project.getBasedir());
        if (checkout.isEmpty())
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        myCheckout = checkout.get();
        onValidateParameters(log, project);
        if (!scope.appliesFamily() && (family != null && !"".equals(family)))
        {
            log.warn("Useless assignment of telanav.family to '" + family + "' when "
                    + "using scope " + scope + " which will not read it.  It is useful "
                    + "only with " + Scope.FAMILY + " and "
                    + Scope.FAMILY_OR_CHILD_FAMILY);
        }
    }

    protected void onValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        // for subclasses
    }

    @Override
    protected final void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        execute(log, project, myCheckout, scope, projectFamily(),
                includeRoot, pretend);
    }

    protected abstract void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            Scope scope, ProjectFamily family, boolean includeRoot,
            boolean pretend) throws Exception;

    @Override
    protected final String overrideProjectFamily()
    {
        return family == null ? null : family.trim();
    }

    protected boolean isPretend()
    {
        return pretend;
    }

    protected boolean isIncludeRoot()
    {
        return includeRoot;
    }
}
