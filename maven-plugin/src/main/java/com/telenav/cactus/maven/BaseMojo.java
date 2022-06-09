package com.telenav.cactus.maven;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.log.BuildLog;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * A base class for our mojos, which sets up a build logger and provides a way
 * to access some commonly needed types.
 *
 * @author Tim Boudreau
 */
abstract class BaseMojo extends AbstractMojo
{

    // These are magically injected by Maven:
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}")
    private MavenSession mavenSession;

    protected BuildLog log;

    private final boolean oncePerSession;

    private ThrowingOptional<ProjectTree> tree;

    protected BaseMojo(boolean oncePerSession)
    {
        this.oncePerSession = oncePerSession;
    }

    protected BaseMojo()
    {
        this(false);
    }

    /**
     * Get the project family for the project the mojo is being run against. The
     * result can be overridden by returning a valid famiily string from
     * <code>overrideProjectFamily()</code> if necessary.
     *
     * @return A project family
     */
    protected final ProjectFamily projectFamily()
    {
        String overriddenFamily = overrideProjectFamily();
        if (overriddenFamily == null || overriddenFamily.isEmpty())
        {
            return ProjectFamily.of(project());
        }
        return ProjectFamily.named(overriddenFamily);
    }

    /**
     * If this mojo allows the project family to be replaced by a parameter, it
     * can provide that here.
     *
     * @return null by default
     */
    protected String overrideProjectFamily()
    {
        return null;
    }

    /**
     * Override to return true if the mojo is intended to run exactly one time
     * for *all* repositories in the checkout, and should not do its work once
     * for every sub-project when called from a multi-module pom.
     *
     * @return true if the mojo should only be run once, on the last project
     */
    protected boolean isOncePerSession()
    {
        return oncePerSession;
    }

    /**
     * Get a project tree for the project this mojo is run on. Note this is an
     * expensive operation.
     *
     * @return An optional
     */
    protected final ThrowingOptional<ProjectTree> projectTree()
    {
        if (tree == null)
        {
            tree = ProjectTree.from(project());
        } else
        {
            tree.ifPresent(ProjectTree::invalidateCache);
        }
        return tree;
    }

    /**
     * Run something against the project tree if one can be constructed.
     *
     * @param <T> The return value type
     * @param func A function applied to the project tree
     * @return An optional result
     */
    protected final <T> ThrowingOptional<T> withProjectTree(ThrowingFunction<ProjectTree, T> func)
    {
        return projectTree().map(func);
    }

    /**
     * Run something against the project tree.
     *
     * @param cons A consumer
     * @return true if the code was run
     */
    protected final boolean withProjectTree(ThrowingConsumer<ProjectTree> cons)
    {
        return projectTree().ifPresent(cons);
    }

    /**
     * Get the build log for this mojo.
     *
     * @return a logger
     */
    protected final BuildLog log()
    {
        if (log == null)
        {
            log = new BuildLog(getClass());
        }
        return log;
    }

    /**
     * Get the project this mojo is invoked against.
     *
     * @return A project
     */
    protected final MavenProject project()
    {
        return project;
    }

    private void internalValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        if (project == null)
        {
            throw new MojoFailureException("Project was not injected");
        }
        if (mavenSession == null)
        {
            throw new MojoFailureException("MavenSession was not injected");
        }
        validateParameters(log, project);
    }

    /**
     * Perform any fail-fast validation here; a super call is not needed.
     *
     * @param log The log
     * @param project A project
     * @throws Exception if something goes wrong
     */
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
    }

    /**
     * Implementation of <code>Mojo.execute()</code>, which delegates to
     * <code>performTasks()</code> after validating the parameters.
     *
     * @throws MojoExecutionException If mojo execution fails
     * @throws MojoFailureException If the mojo could not be executed
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        if (isOncePerSession())
        {
            boolean isRoot = session().getExecutionRootDirectory().equalsIgnoreCase(project.getBasedir().toString());;
            if (!isRoot)
            {
                new BuildLog(getClass()).info("Skipping once-per-session mojo until the end.");
                return;
            }
        }
        run(this::performTasks);
    }

    /**
     * Get the maven session associated with this mojo.
     *
     * @return A session
     */
    protected final MavenSession session()
    {
        return mavenSession;
    }

    protected abstract void performTasks(BuildLog log, MavenProject project) throws Exception;

    private void run(ThrowingBiConsumer<BuildLog, MavenProject> run) throws MojoExecutionException, MojoFailureException
    {
        try
        {
            BuildLog theLog = log();
            theLog.run(() ->
            {
                internalValidateParameters(theLog, project());
                run.accept(theLog, project());
            });
        } catch (MojoFailureException | MojoExecutionException e)
        {
            throw e;
        } catch (Exception | Error e)
        {
            Throwable t = e;
            if (e instanceof java.util.concurrent.CompletionException && e.getCause() != null)
            {
                t = e.getCause();
            }
            throw new MojoFailureException(t);
        }
    }
}
